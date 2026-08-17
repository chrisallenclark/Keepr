import Foundation
import SwiftData
import Testing

@testable import Keepr

/// Cadence is the app telling you who to call today, so it has to be right in
/// both directions: never silent about someone genuinely overdue, and never
/// noisy about someone you didn't ask to be chased about.
@Suite("Cadence")
@MainActor
struct CadenceEngineTests {

    private func client(
        _ store: TestStore,
        name: String = "Client",
        lastInteraction: Date?,
        createdAt: Date = TestDates.days(-365),
        tags: [RelationshipTag] = []
    ) -> Person {
        let person = Make.person(
            store.context,
            given: name,
            family: "Person",
            relationship: .business,
            createdAt: createdAt,
            lastInteractionAt: lastInteraction
        )
        for tag in tags { Make.attach(tag, to: person) }
        return person
    }

    private func tag(_ store: TestStore, _ name: String, days: Int?) -> RelationshipTag {
        let tag = Make.tag(store.context, name: name)
        tag.cadenceDays = days
        return tag
    }

    // MARK: - Where the interval comes from

    @Test("With no rhythm anywhere, the app says nothing")
    func silentByDefault() throws {
        let store = try TestStore()
        let person = client(store, lastInteraction: TestDates.days(-400))

        #expect(CadenceEngine.cadence(for: person) == nil)
        #expect(CadenceEngine.status(for: person, now: TestDates.now) == nil)
        #expect(CadenceEngine.due([person], now: TestDates.now).isEmpty)
    }

    @Test("A type's rhythm applies to everyone with that type")
    func typeCadenceApplies() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(store, lastInteraction: TestDates.days(-31), tags: [clients])

        let cadence = try #require(CadenceEngine.cadence(for: person))
        #expect(cadence.days == 30)
        #expect(cadence.source == "Current Client")
    }

    @Test("Someone with two types is held to the shorter promise")
    func shortestCadenceWins() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let friends = tag(store, "Friend", days: 90)
        let person = client(store, lastInteraction: TestDates.days(-40), tags: [clients, friends])

        #expect(CadenceEngine.cadence(for: person)?.days == 30)
    }

    @Test("A person's own setting overrides their types")
    func personOverridesType() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(store, lastInteraction: TestDates.days(-10), tags: [clients])
        person.cadenceDays = 7

        let cadence = try #require(CadenceEngine.cadence(for: person))
        #expect(cadence.days == 7)
        #expect(cadence.source == nil, "their own setting has no type to attribute it to")
    }

    @Test("Zero means never chase me about this one")
    func zeroMeansNever() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(store, lastInteraction: TestDates.days(-400), tags: [clients])
        person.cadenceDays = 0

        #expect(CadenceEngine.cadence(for: person) == nil)
        #expect(CadenceEngine.due([person], now: TestDates.now).isEmpty)
    }

    // MARK: - Being due

    @Test("The clock runs from the last interaction")
    func dueFromLastInteraction() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(store, lastInteraction: TestDates.days(-45), tags: [clients])

        let status = try #require(
            CadenceEngine.status(for: person, now: TestDates.now, calendar: TestDates.calendar)
        )
        #expect(status.daysOverdue == 15)
        #expect(status.isOverdue)
        #expect(status.summary == "15 days over")
    }

    @Test("Inside the interval, nobody is chased")
    func notYetDue() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(store, lastInteraction: TestDates.days(-10), tags: [clients])

        let status = try #require(
            CadenceEngine.status(for: person, now: TestDates.now, calendar: TestDates.calendar)
        )
        #expect(status.isOverdue == false)
        #expect(status.summary == "In 20 days")
        #expect(CadenceEngine.due([person], now: TestDates.now, calendar: TestDates.calendar).isEmpty)
    }

    @Test("A client imported today isn't instantly overdue")
    func newPeopleGetTheFullInterval() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(
            store,
            lastInteraction: nil,
            createdAt: TestDates.now,
            tags: [clients]
        )

        let status = try #require(
            CadenceEngine.status(for: person, now: TestDates.now, calendar: TestDates.calendar)
        )
        #expect(status.isOverdue == false)
        #expect(CadenceEngine.due([person], now: TestDates.now, calendar: TestDates.calendar).isEmpty)
    }

    @Test("Never contacted and long since added is overdue")
    func neverContactedEventuallyComesDue() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(
            store,
            lastInteraction: nil,
            createdAt: TestDates.days(-60),
            tags: [clients]
        )

        #expect(CadenceEngine.status(for: person, now: TestDates.now, calendar: TestDates.calendar)?.isOverdue == true)
    }

    @Test("Logging anything resets the clock, with no extra step")
    func loggingResetsTheClock() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(store, lastInteraction: TestDates.days(-45), tags: [clients])
        #expect(CadenceEngine.due([person], now: TestDates.now, calendar: TestDates.calendar).count == 1)

        person.lastInteractionAt = TestDates.now

        #expect(CadenceEngine.due([person], now: TestDates.now, calendar: TestDates.calendar).isEmpty)
    }

    @Test("Someone with a plan already isn't chased twice")
    func openFollowUpSuppressesTheNudge() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(store, lastInteraction: TestDates.days(-45), tags: [clients])
        _ = Make.followUp(store.context, due: TestDates.days(2), person: person)

        #expect(CadenceEngine.due([person], now: TestDates.now, calendar: TestDates.calendar).isEmpty)
    }

    @Test("Archived people are left alone")
    func archivedPeopleAreNotChased() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(store, lastInteraction: TestDates.days(-90), tags: [clients])
        person.status = .archived

        #expect(CadenceEngine.status(for: person, now: TestDates.now) == nil)
    }

    @Test("The most overdue person is at the top")
    func mostOverdueFirst() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let slightly = client(store, name: "Slightly", lastInteraction: TestDates.days(-35), tags: [clients])
        let badly = client(store, name: "Badly", lastInteraction: TestDates.days(-120), tags: [clients])

        let due = CadenceEngine.due([slightly, badly], now: TestDates.now, calendar: TestDates.calendar)

        #expect(due.map(\.person.givenName) == ["Badly", "Slightly"])
    }

    // MARK: - Wording

    @Test("Intervals are written the way people say them")
    func intervalsReadNaturally() {
        #expect(CadenceEngine.label(forDays: 7) == "Every week")
        #expect(CadenceEngine.label(forDays: 30) == "Every month")
        #expect(CadenceEngine.label(forDays: 90) == "Every 3 months")
        #expect(CadenceEngine.label(forDays: 45) == "Every 45 days")
    }

    // MARK: - Today

    @Test("Today lists the people a rhythm says are due")
    func digestSurfacesThem() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(store, lastInteraction: TestDates.days(-45), tags: [clients])

        let digest = TodayEngine.digest(
            people: [person],
            mode: .business,
            now: TestDates.now,
            calendar: TestDates.calendar
        )

        #expect(digest.dueForContact.map(\.person.id) == [person.id])
        #expect(digest.isCaughtUp == false)
    }

    @Test("A rhythm replaces the blanket going-quiet rule rather than doubling it")
    func cadenceSuppressesGoingQuiet() throws {
        let store = try TestStore()
        let clients = tag(store, "Current Client", days: 30)
        let person = client(store, lastInteraction: TestDates.days(-45), tags: [clients])

        let digest = TodayEngine.digest(
            people: [person],
            mode: .business,
            now: TestDates.now,
            calendar: TestDates.calendar
        )

        #expect(digest.dueForContact.count == 1)
        #expect(digest.goingQuiet.isEmpty, "one nudge per person, not two")
    }

    @Test("People with no rhythm still fall back to going quiet")
    func withoutCadenceTheOldRuleApplies() throws {
        let store = try TestStore()
        let person = client(store, lastInteraction: TestDates.days(-45))

        let digest = TodayEngine.digest(
            people: [person],
            mode: .business,
            now: TestDates.now,
            calendar: TestDates.calendar
        )

        #expect(digest.dueForContact.isEmpty)
        #expect(digest.goingQuiet.map(\.id) == [person.id])
    }
}
