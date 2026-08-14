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
        PersonGroup.self
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

    /// Seeds the built-in relationship tags exactly once.
    @MainActor
    static func seedIfNeeded(_ context: ModelContext) {
        let descriptor = FetchDescriptor<RelationshipTag>()
        let existing = (try? context.fetch(descriptor)) ?? []
        guard existing.isEmpty else { return }

        for (index, builtIn) in RelationshipTag.builtInCatalog.enumerated() {
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
        }
        try? context.save()
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
    static func deleteAllData(in context: ModelContext) {
        // People cascade to interactions, memories and follow-ups.
        try? context.delete(model: Person.self)
        try? context.delete(model: Interaction.self)
        try? context.delete(model: Memory.self)
        try? context.delete(model: FollowUp.self)
        try? context.delete(model: RelationshipTag.self)
        try? context.delete(model: PersonGroup.self)
        try? context.save()
        seedIfNeeded(context)
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
