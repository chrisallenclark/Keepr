import Foundation
import SwiftData
import Testing

@testable import Keepr

@MainActor
@Suite("FollowUpEngine")
struct FollowUpEngineTests {

    private let now = TestDates.now
    private let calendar = TestDates.calendar

    // MARK: - Due state

    @Test("An all-day follow-up due yesterday is overdue and needs attention")
    func allDayYesterdayIsOverdue() throws {
        let store = try TestStore()
        let followUp = Make.followUp(store.context, due: TestDates.dayStart(-1))

        #expect(followUp.isOverdue(now: now, calendar: calendar))
        #expect(!followUp.isDueToday(now: now, calendar: calendar))
        #expect(followUp.needsAttention(now: now, calendar: calendar))
    }

    @Test("An all-day follow-up due today is due today but not overdue")
    func allDayTodayIsDueToday() throws {
        let store = try TestStore()
        let followUp = Make.followUp(store.context, due: TestDates.dayStart(0))

        #expect(!followUp.isOverdue(now: now, calendar: calendar))
        #expect(followUp.isDueToday(now: now, calendar: calendar))
        #expect(followUp.needsAttention(now: now, calendar: calendar))
    }

    @Test("An all-day follow-up due tomorrow needs no attention yet")
    func allDayTomorrowIsQuiet() throws {
        let store = try TestStore()
        let followUp = Make.followUp(store.context, due: TestDates.dayStart(1))

        #expect(!followUp.isOverdue(now: now, calendar: calendar))
        #expect(!followUp.isDueToday(now: now, calendar: calendar))
        #expect(!followUp.needsAttention(now: now, calendar: calendar))
    }

    @Test("A timed follow-up earlier today is already overdue")
    func timedEarlierTodayIsOverdue() throws {
        let store = try TestStore()
        // now is 12:00; this was due at 09:00.
        let followUp = Make.followUp(store.context, due: TestDates.time(9), hasTime: true)

        #expect(followUp.isOverdue(now: now, calendar: calendar))
        #expect(followUp.isDueToday(now: now, calendar: calendar))
        #expect(followUp.needsAttention(now: now, calendar: calendar))
    }

    @Test("A timed follow-up later today is due today but not overdue")
    func timedLaterTodayIsNotOverdue() throws {
        let store = try TestStore()
        let followUp = Make.followUp(store.context, due: TestDates.time(15), hasTime: true)

        #expect(!followUp.isOverdue(now: now, calendar: calendar))
        #expect(followUp.isDueToday(now: now, calendar: calendar))
        #expect(followUp.needsAttention(now: now, calendar: calendar))
    }

    @Test("A completed follow-up is never overdue or due today")
    func completedIsNeverOverdue() throws {
        let store = try TestStore()
        let allDay = Make.followUp(
            store.context,
            due: TestDates.dayStart(-5),
            isCompleted: true,
            completedAt: TestDates.days(-4)
        )
        let timed = Make.followUp(
            store.context,
            due: TestDates.time(9),
            hasTime: true,
            isCompleted: true,
            completedAt: TestDates.time(10)
        )

        for followUp in [allDay, timed] {
            #expect(!followUp.isOverdue(now: now, calendar: calendar))
            #expect(!followUp.isDueToday(now: now, calendar: calendar))
            #expect(!followUp.needsAttention(now: now, calendar: calendar))
        }
    }

    // MARK: - needingAttention

    @Test("needingAttention orders overdue first, then priority, then earliest due")
    func needingAttentionOrdering() throws {
        let store = try TestStore()
        let overdueHigh = Make.followUp(
            store.context,
            title: "overdue high",
            due: TestDates.dayStart(-1),
            priority: .high
        )
        let overdueOld = Make.followUp(
            store.context,
            title: "overdue old",
            due: TestDates.dayStart(-5)
        )
        let overdueRecent = Make.followUp(
            store.context,
            title: "overdue recent",
            due: TestDates.dayStart(-2)
        )
        let todayHigh = Make.followUp(
            store.context,
            title: "today high",
            due: TestDates.time(15),
            hasTime: true,
            priority: .high
        )
        let todayNormal = Make.followUp(
            store.context,
            title: "today normal",
            due: TestDates.time(18),
            hasTime: true
        )
        let tomorrow = Make.followUp(
            store.context,
            title: "tomorrow",
            due: TestDates.dayStart(1)
        )
        let completed = Make.followUp(
            store.context,
            title: "completed",
            due: TestDates.dayStart(-3),
            isCompleted: true
        )

        let result = FollowUpEngine.needingAttention(
            [todayNormal, completed, overdueRecent, tomorrow, overdueHigh, todayHigh, overdueOld],
            now: now,
            calendar: calendar
        )

        #expect(
            result.map(\.title) == [
                "overdue high",
                "overdue old",
                "overdue recent",
                "today high",
                "today normal"
            ]
        )
    }

    @Test("needingAttention is empty when nothing is due")
    func needingAttentionEmpty() throws {
        let store = try TestStore()
        let future = Make.followUp(store.context, due: TestDates.dayStart(4))

        #expect(FollowUpEngine.needingAttention([future], now: now, calendar: calendar).isEmpty)
    }

    // MARK: - upcoming

    @Test("upcoming excludes today and anything past the window")
    func upcomingWindow() throws {
        let store = try TestStore()
        let today = Make.followUp(store.context, title: "today", due: TestDates.dayStart(0))
        let todayEvening = Make.followUp(
            store.context,
            title: "today evening",
            due: TestDates.time(18),
            hasTime: true
        )
        let tomorrow = Make.followUp(store.context, title: "tomorrow", due: TestDates.dayStart(1))
        let edge = Make.followUp(store.context, title: "day 7", due: TestDates.dayStart(7))
        let past = Make.followUp(store.context, title: "day 8", due: TestDates.dayStart(8))
        let completed = Make.followUp(
            store.context,
            title: "completed",
            due: TestDates.dayStart(2),
            isCompleted: true
        )

        let result = FollowUpEngine.upcoming(
            [past, edge, todayEvening, tomorrow, completed, today],
            withinDays: 7,
            now: now,
            calendar: calendar
        )

        #expect(result.map(\.title) == ["tomorrow", "day 7"])
    }

    // MARK: - bucket

    @Test("bucket maps a follow-up to its section")
    func bucketMapping() throws {
        let store = try TestStore()
        let overdue = Make.followUp(store.context, due: TestDates.dayStart(-1))
        let today = Make.followUp(store.context, due: TestDates.dayStart(0))
        let thisWeek = Make.followUp(store.context, due: TestDates.dayStart(3))
        let weekEdge = Make.followUp(store.context, due: TestDates.dayStart(7))
        let later = Make.followUp(store.context, due: TestDates.dayStart(10))
        // Completed wins over overdue.
        let completed = Make.followUp(
            store.context,
            due: TestDates.dayStart(-9),
            isCompleted: true
        )

        #expect(FollowUpEngine.bucket(for: overdue, now: now, calendar: calendar) == .overdue)
        #expect(FollowUpEngine.bucket(for: today, now: now, calendar: calendar) == .today)
        #expect(FollowUpEngine.bucket(for: thisWeek, now: now, calendar: calendar) == .thisWeek)
        #expect(FollowUpEngine.bucket(for: weekEdge, now: now, calendar: calendar) == .thisWeek)
        #expect(FollowUpEngine.bucket(for: later, now: now, calendar: calendar) == .later)
        #expect(FollowUpEngine.bucket(for: completed, now: now, calendar: calendar) == .completed)
    }

    // MARK: - grouped

    @Test("grouped keeps bucket order and drops empty buckets")
    func groupedOrderAndEmptyBuckets() throws {
        let store = try TestStore()
        let overdue = Make.followUp(store.context, due: TestDates.dayStart(-2))
        let today = Make.followUp(store.context, due: TestDates.dayStart(0))
        let later = Make.followUp(store.context, due: TestDates.dayStart(21))
        let completed = Make.followUp(
            store.context,
            due: TestDates.dayStart(-4),
            isCompleted: true,
            completedAt: TestDates.days(-3)
        )

        // No .thisWeek item here, so that section must not appear.
        let groups = FollowUpEngine.grouped(
            [later, completed, today, overdue],
            now: now,
            calendar: calendar
        )

        #expect(groups.map({ $0.bucket }) == [.overdue, .today, .later, .completed])
        #expect(groups.allSatisfy({ !$0.items.isEmpty }))
    }

    @Test("grouped sorts open items by due date then priority")
    func groupedSortsOpenItems() throws {
        let store = try TestStore()
        let early = Make.followUp(store.context, title: "early", due: TestDates.dayStart(-4))
        let lateNormal = Make.followUp(store.context, title: "late normal", due: TestDates.dayStart(-1))
        let lateHigh = Make.followUp(
            store.context,
            title: "late high",
            due: TestDates.dayStart(-1),
            priority: .high
        )

        let groups = FollowUpEngine.grouped(
            [lateNormal, lateHigh, early],
            now: now,
            calendar: calendar
        )
        let overdue = try #require(groups.first)

        #expect(overdue.bucket == .overdue)
        #expect(overdue.items.map(\.title) == ["early", "late high", "late normal"])
    }

    @Test("grouped applies completedLimit, keeping the most recently completed")
    func groupedAppliesCompletedLimit() throws {
        let store = try TestStore()
        let newest = Make.followUp(
            store.context,
            title: "newest",
            due: TestDates.dayStart(-1),
            isCompleted: true,
            completedAt: TestDates.days(-1)
        )
        let middle = Make.followUp(
            store.context,
            title: "middle",
            due: TestDates.dayStart(-2),
            isCompleted: true,
            completedAt: TestDates.days(-2)
        )
        let oldest = Make.followUp(
            store.context,
            title: "oldest",
            due: TestDates.dayStart(-3),
            isCompleted: true,
            completedAt: TestDates.days(-3)
        )

        let groups = FollowUpEngine.grouped(
            [middle, oldest, newest],
            now: now,
            calendar: calendar,
            completedLimit: 2
        )
        let completed = try #require(groups.first)

        #expect(completed.bucket == .completed)
        #expect(completed.items.map(\.title) == ["newest", "middle"])
    }

    // MARK: - snooze

    @Test("snooze preserves time-of-day when hasTime is true")
    func snoozePreservesTime() throws {
        let store = try TestStore()
        let followUp = Make.followUp(store.context, due: TestDates.time(15), hasTime: true)

        followUp.snooze(byDays: 2, from: now, calendar: calendar)

        #expect(followUp.dueDate == TestDates.time(15, dayOffset: 2))
        #expect(followUp.lastSnoozedAt == now)
    }

    @Test("snooze snaps to start of day when hasTime is false")
    func snoozeSnapsToStartOfDay() throws {
        let store = try TestStore()
        let followUp = Make.followUp(store.context, due: TestDates.dayStart(1))

        followUp.snooze(byDays: 3, from: now, calendar: calendar)

        #expect(followUp.dueDate == TestDates.dayStart(4))
    }

    @Test("Snoozing an overdue follow-up moves it relative to now, not its old due date")
    func snoozeOverdueMovesFromNow() throws {
        let store = try TestStore()
        let allDay = Make.followUp(store.context, due: TestDates.dayStart(-10))
        let timed = Make.followUp(
            store.context,
            due: TestDates.time(9, dayOffset: -10),
            hasTime: true
        )

        allDay.snooze(byDays: 1, from: now, calendar: calendar)
        timed.snooze(byDays: 1, from: now, calendar: calendar)

        #expect(allDay.dueDate == TestDates.dayStart(1))
        #expect(timed.dueDate == TestDates.days(1))
    }

    // MARK: - DuePreset

    @Test("DuePreset.date returns start of day N days out")
    func duePresetDates() throws {
        for preset in FollowUpEngine.DuePreset.allCases {
            #expect(
                preset.date(from: now, calendar: calendar) == TestDates.dayStart(preset.days)
            )
        }
        #expect(
            FollowUpEngine.DuePreset.tomorrow.date(from: now, calendar: calendar)
                == TestDates.dayStart(1)
        )
        #expect(FollowUpEngine.DuePreset.nextMonth.days == 30)
    }
}
