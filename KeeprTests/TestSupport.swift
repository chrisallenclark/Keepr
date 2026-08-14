import Foundation
import SwiftData
import Testing

@testable import Keepr

// MARK: - Store

/// A throwaway in-memory SwiftData stack. One per test keeps tests isolated.
///
/// `ModelContext` is not `Sendable`, so every suite that touches one is
/// annotated `@MainActor`.
@MainActor
struct TestStore {

    let container: ModelContainer
    let context: ModelContext

    init() throws {
        let schema = Schema([
            Person.self,
            Interaction.self,
            Memory.self,
            FollowUp.self,
            RelationshipTag.self,
            PersonGroup.self,
            PersonLink.self
        ])
        let configuration = ModelConfiguration(isStoredInMemoryOnly: true)
        container = try ModelContainer(for: schema, configurations: configuration)
        context = ModelContext(container)
    }

    func save() throws {
        try context.save()
    }

    func fetch<T: PersistentModel>(_ type: T.Type) throws -> [T] {
        try context.fetch(FetchDescriptor<T>())
    }
}

// MARK: - Deterministic dates

/// Every date in the suite is derived from one fixed reference instant in UTC,
/// so nothing here can drift with the wall clock, the machine's locale or DST.
enum TestDates {

    /// Gregorian, UTC, POSIX locale — passed explicitly into every engine call.
    static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .gmt
        calendar.locale = Locale(identifier: "en_US_POSIX")
        return calendar
    }()

    static func date(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0
    ) -> Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        components.second = 0
        return calendar.date(from: components) ?? Date(timeIntervalSince1970: 0)
    }

    /// The fixed "now" for the whole suite: Sunday 15 June 2025, 12:00 UTC.
    static let now: Date = date(year: 2025, month: 6, day: 15, hour: 12, minute: 0)

    /// `now` shifted by whole days, keeping the 12:00 time-of-day.
    static func days(_ offset: Int, from base: Date = TestDates.now) -> Date {
        calendar.date(byAdding: .day, value: offset, to: base) ?? base
    }

    /// Midnight of the day `offset` days away from `now`.
    static func dayStart(_ offset: Int = 0) -> Date {
        calendar.startOfDay(for: days(offset))
    }

    /// A specific wall-clock time on the day `dayOffset` days away from `now`.
    static func time(_ hour: Int, _ minute: Int = 0, dayOffset: Int = 0) -> Date {
        let day = days(dayOffset)
        return calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day) ?? day
    }
}

// MARK: - Factories

/// Small builders that insert into a context so SwiftData maintains inverses.
@MainActor
enum Make {

    static func person(
        _ context: ModelContext,
        given: String = "Test",
        family: String = "Person",
        preferred: String? = nil,
        relationship: RelationshipContext = .personal,
        company: String? = nil,
        jobTitle: String? = nil,
        priority: Priority = .normal,
        isFavorite: Bool = false,
        status: RelationshipStatus = .active,
        howWeMet: String? = nil,
        introducedBy: String? = nil,
        notes: String? = nil,
        contactIdentifier: String? = nil,
        createdAt: Date = TestDates.now,
        lastInteractionAt: Date? = nil
    ) -> Person {
        let person = Person(
            givenName: given,
            familyName: family,
            preferredName: preferred,
            context: relationship,
            contactIdentifier: contactIdentifier,
            company: company,
            jobTitle: jobTitle,
            priority: priority,
            isFavorite: isFavorite,
            howWeMet: howWeMet,
            introducedBy: introducedBy,
            notes: notes,
            createdAt: createdAt
        )
        context.insert(person)
        person.status = status
        person.lastInteractionAt = lastInteractionAt
        return person
    }

    static func tag(
        _ context: ModelContext,
        name: String,
        kind: TagKind = .business,
        isBuiltIn: Bool = false,
        sortOrder: Int = 0,
        symbolName: String = "tag"
    ) -> RelationshipTag {
        let tag = RelationshipTag(
            name: name,
            kind: kind,
            isBuiltIn: isBuiltIn,
            sortOrder: sortOrder,
            symbolName: symbolName
        )
        context.insert(tag)
        return tag
    }

    /// Attaches from the `Person` side only; SwiftData fills in `tag.people`.
    static func attach(_ tag: RelationshipTag, to person: Person) {
        var tags = person.tags ?? []
        guard !tags.contains(where: { $0 === tag }) else { return }
        tags.append(tag)
        person.tags = tags
    }

    static func interaction(
        _ context: ModelContext,
        kind: InteractionKind = .other,
        occurredAt: Date = TestDates.now,
        title: String? = nil,
        rawNote: String? = nil,
        summary: String? = nil,
        isQuickCapture: Bool = false,
        person: Person? = nil
    ) -> Interaction {
        let interaction = Interaction(
            kind: kind,
            occurredAt: occurredAt,
            title: title,
            rawNote: rawNote,
            summary: summary,
            isQuickCapture: isQuickCapture
        )
        context.insert(interaction)
        interaction.person = person
        return interaction
    }

    static func memory(
        _ context: ModelContext,
        content: String,
        category: MemoryCategory = .other,
        importance: Priority = .normal,
        createdAt: Date = TestDates.now,
        isArchived: Bool = false,
        person: Person? = nil,
        sourceInteraction: Interaction? = nil
    ) -> Memory {
        let memory = Memory(
            content: content,
            category: category,
            importance: importance,
            createdAt: createdAt
        )
        context.insert(memory)
        memory.isArchived = isArchived
        memory.person = person
        memory.sourceInteraction = sourceInteraction
        return memory
    }

    static func followUp(
        _ context: ModelContext,
        title: String = "Follow up",
        note: String? = nil,
        due: Date,
        hasTime: Bool = false,
        priority: Priority = .normal,
        isCompleted: Bool = false,
        completedAt: Date? = nil,
        createdAt: Date = TestDates.now,
        person: Person? = nil,
        sourceInteraction: Interaction? = nil
    ) -> FollowUp {
        let followUp = FollowUp(
            title: title,
            note: note,
            dueDate: due,
            hasTime: hasTime,
            priority: priority,
            createdAt: createdAt
        )
        context.insert(followUp)
        followUp.person = person
        followUp.sourceInteraction = sourceInteraction
        if isCompleted {
            followUp.complete(at: completedAt ?? TestDates.now)
        }
        return followUp
    }
}
