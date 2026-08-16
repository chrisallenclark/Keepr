import Foundation
import OSLog
import SwiftData

/// Builds the app's SwiftData container and performs first-run seeding.
///
/// The persistence surface is deliberately this small: everything else in the
/// app talks to `ModelContext` directly through the SwiftUI environment. When
/// CloudKit sync is switched on, only this file changes.
enum KeeprStore {

    static let schema = Schema([
        Person.self,
        Interaction.self,
        Memory.self,
        FollowUp.self,
        RelationshipTag.self,
        PersonGroup.self,
        PersonLink.self
    ])

    /// True when the on-disk store could not be opened and the app is running
    /// against a temporary in-memory store. Surfaced in Settings so the user is
    /// never quietly told their data saved when it didn't.
    @MainActor private(set) static var isUsingFallbackStore = false

    @MainActor
    static func makeContainer(inMemory: Bool = false) -> ModelContainer {
        let configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory)

        if let container = try? ModelContainer(for: schema, configurations: configuration) {
            seedIfNeeded(container.mainContext)
            return container
        }

        // Never log the error object itself — it can carry store paths and, in
        // migration failures, property values.
        Logger.persistence.error("Persistent store unavailable; falling back to in-memory store.")
        isUsingFallbackStore = true

        let fallback = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        guard let container = try? ModelContainer(for: schema, configurations: fallback) else {
            // A memory-only container failing means the process is unusable.
            fatalError("Unable to create a SwiftData container.")
        }
        seedIfNeeded(container.mainContext)
        return container
    }

    /// Key holding the built-ins this install has already been offered.
    private static let seededKeysDefault = "keepr.seededBuiltInKeys"

    /// Seeds built-in relationship types, including ones added in later versions.
    ///
    /// Each built-in is offered exactly once *ever*, tracked by key rather than
    /// by "is the table empty". That gets both halves right: someone who
    /// installed before "Mentor" existed still receives it on update, and
    /// someone who deliberately deleted "Vendor" doesn't have it reappear every
    /// launch. Deleting a built-in is a decision, not an accident to repair.
    @MainActor
    static func seedIfNeeded(_ context: ModelContext, defaults: UserDefaults = .standard) {
        repairTagCatalog(context)

        let descriptor = FetchDescriptor<RelationshipTag>()
        let existing = (try? context.fetch(descriptor)) ?? []

        // Identity by key *and* by visible name. Keys were added to the model
        // after the first releases, so rows seeded before that carry none —
        // trusting keys alone once made the app decide every built-in was
        // missing and seed a second copy of all of them.
        let existingKeys = Set(existing.compactMap(\.builtInKey))
            .union(existing.map(\.name))
        var offered = Set(defaults.stringArray(forKey: seededKeysDefault) ?? [])

        // A store with tags but no record of what was seeded predates this
        // bookkeeping. Treat what it holds as already offered so nothing is
        // duplicated. The one cost: a built-in deleted before this update comes
        // back once, and is then remembered. Paid a single time, at most.
        if offered.isEmpty, !existing.isEmpty {
            offered = existingKeys
        }

        var didInsert = false
        for (index, builtIn) in RelationshipTag.builtInCatalog.enumerated() {
            guard !offered.contains(builtIn.name), !existingKeys.contains(builtIn.name) else {
                continue
            }
            context.insert(
                RelationshipTag(
                    name: builtIn.name,
                    kind: builtIn.kind,
                    isBuiltIn: true,
                    sortOrder: index * 10,
                    symbolName: builtIn.symbolName,
                    builtInKey: builtIn.name
                )
            )
            offered.insert(builtIn.name)
            didInsert = true
        }

        // Always write the record, even when nothing was inserted. Otherwise an
        // install that needed no seeding never gets one, and the first type the
        // user deletes comes back on the next launch.
        offered.formUnion(existingKeys)
        defaults.set(Array(offered).sorted(), forKey: seededKeysDefault)

        guard didInsert else { return }
        try? context.save()
    }

    /// Merges duplicate relationship types and gives old built-ins their key.
    ///
    /// Two types with the same name and kind are indistinguishable everywhere in
    /// the app, so there's no such thing as a deliberate pair — every one is
    /// damage, and this is the only place that can undo it. People marked with a
    /// discarded copy are moved to the survivor first; nobody loses a type
    /// because of a bug in seeding.
    @MainActor
    static func repairTagCatalog(_ context: ModelContext) {
        let all = (try? context.fetch(FetchDescriptor<RelationshipTag>())) ?? []
        guard !all.isEmpty else { return }
        var didChange = false

        // Backfill keys first, so a merge keeps the row that knows what it is.
        let catalog = Dictionary(
            uniqueKeysWithValues: RelationshipTag.builtInCatalog.map { ("\($0.kind.rawValue)|\($0.name)", $0) }
        )
        for tag in all where tag.builtInKey == nil {
            guard catalog["\(tag.kind.rawValue)|\(tag.name)"] != nil else { continue }
            tag.builtInKey = tag.name
            tag.isBuiltIn = true
            didChange = true
        }

        var groups: [String: [RelationshipTag]] = [:]
        for tag in all {
            let key = "\(tag.kind.rawValue)|\(tag.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased())"
            groups[key, default: []].append(tag)
        }

        for duplicates in groups.values where duplicates.count > 1 {
            // Keep the one people are actually marked with; ties go to the
            // oldest, which is the one that was there before the bug.
            let ordered = duplicates.sorted { lhs, rhs in
                if lhs.peopleList.count != rhs.peopleList.count {
                    return lhs.peopleList.count > rhs.peopleList.count
                }
                return lhs.createdAt < rhs.createdAt
            }
            guard let survivor = ordered.first else { continue }

            for extra in ordered.dropFirst() {
                for person in extra.peopleList where !person.tagList.contains(where: { $0.id == survivor.id }) {
                    person.tags = person.tagList.filter { $0.id != extra.id } + [survivor]
                }
                context.delete(extra)
                didChange = true
            }
        }

        guard didChange else { return }
        try? context.save()
        Logger.persistence.notice("Repaired the relationship type catalog.")
    }

    /// Fetches a tag, creating it if the user has deleted or never had it.
    ///
    /// `name` doubles as the built-in key, so a built-in the user has renamed is
    /// still found. Matching on the visible name alone would mean renaming
    /// "Family" quietly produced a second "Family" on the next import.
    @MainActor
    static func tag(named name: String, kind: TagKind, in context: ModelContext) -> RelationshipTag {
        let descriptor = FetchDescriptor<RelationshipTag>()
        let all = (try? context.fetch(descriptor)) ?? []
        if let match = all.first(where: { $0.builtInKey == name && $0.kind == kind }) {
            return match
        }
        if let match = all.first(where: { $0.name == name && $0.kind == kind }) {
            return match
        }
        let created = RelationshipTag(
            name: name,
            kind: kind,
            sortOrder: (all.map(\.sortOrder).max() ?? 0) + 10
        )
        context.insert(created)
        return created
    }

    /// Wipes every record the app owns. Backing Apple contacts are untouched.
    @MainActor
    static func deleteAllData(in context: ModelContext, defaults: UserDefaults = .standard) {
        // People cascade to interactions, memories and follow-ups.
        try? context.delete(model: Person.self)
        try? context.delete(model: Interaction.self)
        try? context.delete(model: Memory.self)
        try? context.delete(model: FollowUp.self)
        try? context.delete(model: RelationshipTag.self)
        try? context.delete(model: PersonGroup.self)
        try? context.delete(model: PersonLink.self)
        try? context.save()
        // Erasing means starting over, so the built-ins come back rather than
        // leaving someone with an empty palette and no way to get it back.
        defaults.removeObject(forKey: seededKeysDefault)
        seedIfNeeded(context, defaults: defaults)
    }
}

// MARK: - Logging

extension Logger {
    private static let subsystem = Bundle.main.bundleIdentifier ?? "com.chrisallenclark.Keepr"

    /// Metadata only. Relationship content must never be logged.
    static let persistence = Logger(subsystem: subsystem, category: "persistence")
    static let contacts = Logger(subsystem: subsystem, category: "contacts")
    static let notifications = Logger(subsystem: subsystem, category: "notifications")
}
