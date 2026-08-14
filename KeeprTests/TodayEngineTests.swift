import Foundation
import SwiftData
import Testing

@testable import Keepr

@MainActor
@Suite("TodayEngine")
struct TodayEngineTests {

    private let now = TestDates.now
    private let calendar = TestDates.calendar

    // MARK: - goingQuiet

    @Test("Thresholds differ per mode: 21 days for business, 60 for personal")
    func quietThresholds() {
        #expect(TodayEngine.quietThreshold(for: .business) == 21)
        #expect(TodayEngine.quietThreshold(for: .personal) == 60)
    }

    @Test("Someone past the mode's threshold is going quiet; someone inside it is not")
    func goingQuietUsesThreshold() throws {
        let store = try TestStore()
        let drifted = Make.person(
            store.context,
            given: "Dee",
            family: "Drifted",
            relationship: .both,
            lastInteractionAt: TestDates.days(-30)
        )
        let fresh = Make.person(
            store.context,
            given: "Fay",
            family: "Fresh",
            relationship: .both,
            lastInteractionAt: TestDates.days(-3)
        )

        let business = TodayEngine.goingQuiet(
            [drifted, fresh],
            mode: .business,
            now: now,
            calendar: calendar
        )
        #expect(business.map(\.id) == [drifted.id])

        // 30 days is still comfortably inside the 60-day personal threshold.
        let personal = TodayEngine.goingQuiet(
            [drifted, fresh],
            mode: .personal,
            now: now,
            calendar: calendar
        )
        #expect(personal.isEmpty)
    }

    @Test("A person past the personal threshold is going quiet in personal mode")
    func goingQuietPersonalThreshold() throws {
        let store = try TestStore()
        let veryQuiet = Make.person(
            store.context,
            given: "Vic",
            family: "Quiet",
            relationship: .personal,
            lastInteractionAt: TestDates.days(-90)
        )

        let result = TodayEngine.goingQuiet(
            [veryQuiet],
            mode: .personal,
            now: now,
            calendar: calendar
        )
        #expect(result.map(\.id) == [veryQuiet.id])
    }

    @Test("An open follow-up excludes someone from going quiet; a completed one does not")
    func goingQuietSkipsPeopleWithOpenFollowUps() throws {
        let store = try TestStore()
        let planned = Make.person(
            store.context,
            given: "Pia",
            family: "Planned",
            relationship: .business,
            lastInteractionAt: TestDates.days(-40)
        )
        let done = Make.person(
            store.context,
            given: "Dan",
            family: "Done",
            relationship: .business,
            lastInteractionAt: TestDates.days(-40)
        )
        _ = Make.followUp(store.context, due: TestDates.dayStart(2), person: planned)
        _ = Make.followUp(
            store.context,
            due: TestDates.dayStart(-2),
            isCompleted: true,
            person: done
        )

        let result = TodayEngine.goingQuiet(
            [planned, done],
            mode: .business,
            now: now,
            calendar: calendar
        )
        #expect(result.map(\.id) == [done.id])
    }

    @Test("A never-contacted person surfaces only when flagged and created before the cutoff")
    func goingQuietNeverContacted() throws {
        let store = try TestStore()
        let ordinary = Make.person(
            store.context,
            given: "Ora",
            family: "Ordinary",
            relationship: .business,
            createdAt: TestDates.days(-100)
        )
        let favoriteOld = Make.person(
            store.context,
            given: "Fay",
            family: "FavOld",
            relationship: .business,
            isFavorite: true,
            createdAt: TestDates.days(-100)
        )
        let favoriteNew = Make.person(
            store.context,
            given: "Fin",
            family: "FavNew",
            relationship: .business,
            isFavorite: true,
            createdAt: TestDates.days(-2)
        )
        let highPriorityOld = Make.person(
            store.context,
            given: "Hal",
            family: "HighOld",
            relationship: .business,
            priority: .high,
            createdAt: TestDates.days(-100)
        )

        let result = TodayEngine.goingQuiet(
            [ordinary, favoriteOld, favoriteNew, highPriorityOld],
            mode: .business,
            now: now,
            calendar: calendar
        )

        let ids = Set(result.map(\.id))
        #expect(ids == Set([favoriteOld.id, highPriorityOld.id]))
    }

    @Test("goingQuiet sorts the longest-silent relationship first")
    func goingQuietOrdering() throws {
        let store = try TestStore()
        let quietest = Make.person(
            store.context,
            given: "Qui",
            family: "Quietest",
            relationship: .business,
            lastInteractionAt: TestDates.days(-90)
        )
        let middle = Make.person(
            store.context,
            given: "Mid",
            family: "Middle",
            relationship: .business,
            lastInteractionAt: TestDates.days(-50)
        )
        let recent = Make.person(
            store.context,
            given: "Ron",
            family: "Recent",
            relationship: .business,
            lastInteractionAt: TestDates.days(-25)
        )

        let result = TodayEngine.goingQuiet(
            [middle, recent, quietest],
            mode: .business,
            now: now,
            calendar: calendar
        )
        #expect(result.map(\.id) == [quietest.id, middle.id, recent.id])
    }

    // MARK: - recentlyActive

    @Test("recentlyActive returns the last 14 days, most recent first")
    func recentlyActiveDefaultWindow() throws {
        let store = try TestStore()
        let yesterday = Make.person(
            store.context,
            given: "Yara",
            family: "Yesterday",
            lastInteractionAt: TestDates.days(-1)
        )
        let lastWeek = Make.person(
            store.context,
            given: "Lee",
            family: "LastWeek",
            lastInteractionAt: TestDates.days(-6)
        )
        let outside = Make.person(
            store.context,
            given: "Oda",
            family: "Outside",
            lastInteractionAt: TestDates.days(-20)
        )
        let never = Make.person(store.context, given: "Nev", family: "Never")

        let result = TodayEngine.recentlyActive(
            [outside, never, lastWeek, yesterday],
            now: now,
            calendar: calendar
        )
        #expect(result.map(\.id) == [yesterday.id, lastWeek.id])
    }

    @Test("recentlyActive honours a custom window")
    func recentlyActiveCustomWindow() throws {
        let store = try TestStore()
        let yesterday = Make.person(
            store.context,
            given: "Yara",
            family: "Yesterday",
            lastInteractionAt: TestDates.days(-1)
        )
        let lastWeek = Make.person(
            store.context,
            given: "Lee",
            family: "LastWeek",
            lastInteractionAt: TestDates.days(-6)
        )

        let result = TodayEngine.recentlyActive(
            [lastWeek, yesterday],
            withinDays: 3,
            now: now,
            calendar: calendar
        )
        #expect(result.map(\.id) == [yesterday.id])
    }

    // MARK: - digest

    @Test("digest only considers people visible in the current mode")
    func digestRespectsMode() throws {
        let store = try TestStore()
        let workPerson = Make.person(
            store.context,
            given: "Wes",
            family: "Work",
            relationship: .business
        )
        let homePerson = Make.person(
            store.context,
            given: "Hal",
            family: "Home",
            relationship: .personal
        )
        _ = Make.followUp(
            store.context,
            title: "work task",
            due: TestDates.dayStart(-1),
            person: workPerson
        )
        _ = Make.followUp(
            store.context,
            title: "home task",
            due: TestDates.dayStart(-1),
            person: homePerson
        )

        let business = TodayEngine.digest(
            people: [workPerson, homePerson],
            mode: .business,
            now: now,
            calendar: calendar
        )
        #expect(business.needsAttention.map(\.title) == ["work task"])

        let personal = TodayEngine.digest(
            people: [workPerson, homePerson],
            mode: .personal,
            now: now,
            calendar: calendar
        )
        #expect(personal.needsAttention.map(\.title) == ["home task"])
    }

    @Test("digest caps goingQuiet at goingQuietLimit")
    func digestGoingQuietLimit() throws {
        let store = try TestStore()
        var people: [Person] = []
        for index in 0..<7 {
            people.append(
                Make.person(
                    store.context,
                    given: "Q\(index)",
                    family: "Quiet\(index)",
                    relationship: .business,
                    lastInteractionAt: TestDates.days(-30 - index)
                )
            )
        }

        let digest = TodayEngine.digest(
            people: people,
            mode: .business,
            now: now,
            calendar: calendar
        )

        #expect(TodayEngine.goingQuietLimit == 5)
        #expect(digest.goingQuiet.count == 5)
        // The longest-silent person is the one with the largest negative offset.
        #expect(digest.goingQuiet.first?.id == people[6].id)
    }

    @Test("digest caps recent at recentLimit")
    func digestRecentLimit() throws {
        let store = try TestStore()
        var people: [Person] = []
        for index in 0..<8 {
            people.append(
                Make.person(
                    store.context,
                    given: "R\(index)",
                    family: "Recent\(index)",
                    relationship: .personal,
                    lastInteractionAt: TestDates.days(-1 - index)
                )
            )
        }

        let digest = TodayEngine.digest(
            people: people,
            mode: .personal,
            now: now,
            calendar: calendar
        )

        #expect(TodayEngine.recentLimit == 6)
        #expect(digest.recent.count == 6)
        #expect(digest.recent.first?.id == people[0].id)
    }

    @Test("digest caps upcoming at upcomingLimit and uses the 7-day window")
    func digestUpcomingLimit() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Ula",
            family: "Upcoming",
            relationship: .business
        )
        for day in 1...7 {
            _ = Make.followUp(
                store.context,
                title: "day \(day)",
                due: TestDates.dayStart(day),
                person: person
            )
        }

        let digest = TodayEngine.digest(
            people: [person],
            mode: .business,
            now: now,
            calendar: calendar
        )

        #expect(TodayEngine.upcomingWindowDays == 7)
        #expect(TodayEngine.upcomingLimit == 5)
        #expect(digest.upcoming.map(\.title) == ["day 1", "day 2", "day 3", "day 4", "day 5"])
    }

    @Test("isCaughtUp is true exactly when needsAttention is empty")
    func digestIsCaughtUp() throws {
        let store = try TestStore()
        let person = Make.person(
            store.context,
            given: "Cal",
            family: "Calm",
            relationship: .business
        )
        _ = Make.followUp(store.context, title: "later", due: TestDates.dayStart(3), person: person)

        let calm = TodayEngine.digest(
            people: [person],
            mode: .business,
            now: now,
            calendar: calendar
        )
        #expect(calm.needsAttention.isEmpty)
        #expect(calm.isCaughtUp)
        #expect(!calm.isEmpty)

        _ = Make.followUp(store.context, title: "overdue", due: TestDates.dayStart(-1), person: person)

        let busy = TodayEngine.digest(
            people: [person],
            mode: .business,
            now: now,
            calendar: calendar
        )
        #expect(busy.needsAttention.count == 1)
        #expect(!busy.isCaughtUp)
    }

    @Test("An empty digest is both empty and caught up")
    func digestEmpty() throws {
        let digest = TodayEngine.digest(
            people: [],
            mode: .business,
            now: now,
            calendar: calendar
        )
        #expect(digest.isEmpty)
        #expect(digest.isCaughtUp)
    }
}
