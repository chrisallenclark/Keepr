package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.domain.FollowUpBucket
import com.aulorvy.keepr.core.domain.FollowUpEngine
import com.aulorvy.keepr.core.model.Priority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Translated from KeeprTests/FollowUpEngineTests.swift. Test names are the Swift
// function names; the Swift display name is in the comment above each.
class FollowUpEngineTests {

    private val now = TestDates.now
    private val calendar = TestDates.calendar

    // Due state

    /** An all-day follow-up due yesterday is overdue and needs attention */
    @Test
    fun allDayYesterdayIsOverdue() {
        val store = TestStore()
        val followUp = Make.followUp(store.context, due = TestDates.dayStart(-1))

        assertTrue(followUp.isOverdue(now, calendar))
        assertFalse(followUp.isDueToday(now, calendar))
        assertTrue(followUp.needsAttention(now, calendar))
    }

    /** An all-day follow-up due today is due today but not overdue */
    @Test
    fun allDayTodayIsDueToday() {
        val store = TestStore()
        val followUp = Make.followUp(store.context, due = TestDates.dayStart(0))

        assertFalse(followUp.isOverdue(now, calendar))
        assertTrue(followUp.isDueToday(now, calendar))
        assertTrue(followUp.needsAttention(now, calendar))
    }

    /** An all-day follow-up due tomorrow needs no attention yet */
    @Test
    fun allDayTomorrowIsQuiet() {
        val store = TestStore()
        val followUp = Make.followUp(store.context, due = TestDates.dayStart(1))

        assertFalse(followUp.isOverdue(now, calendar))
        assertFalse(followUp.isDueToday(now, calendar))
        assertFalse(followUp.needsAttention(now, calendar))
    }

    /** A timed follow-up earlier today is already overdue */
    @Test
    fun timedEarlierTodayIsOverdue() {
        val store = TestStore()
        // now is 12:00; this was due at 09:00.
        val followUp = Make.followUp(store.context, due = TestDates.time(9), hasTime = true)

        assertTrue(followUp.isOverdue(now, calendar))
        assertTrue(followUp.isDueToday(now, calendar))
        assertTrue(followUp.needsAttention(now, calendar))
    }

    /** A timed follow-up later today is due today but not overdue */
    @Test
    fun timedLaterTodayIsNotOverdue() {
        val store = TestStore()
        val followUp = Make.followUp(store.context, due = TestDates.time(15), hasTime = true)

        assertFalse(followUp.isOverdue(now, calendar))
        assertTrue(followUp.isDueToday(now, calendar))
        assertTrue(followUp.needsAttention(now, calendar))
    }

    /** A completed follow-up is never overdue or due today */
    @Test
    fun completedIsNeverOverdue() {
        val store = TestStore()
        val allDay = Make.followUp(
            store.context,
            due = TestDates.dayStart(-5),
            isCompleted = true,
            completedAt = TestDates.days(-4),
        )
        val timed = Make.followUp(
            store.context,
            due = TestDates.time(9),
            hasTime = true,
            isCompleted = true,
            completedAt = TestDates.time(10),
        )

        for (followUp in listOf(allDay, timed)) {
            assertFalse(followUp.isOverdue(now, calendar))
            assertFalse(followUp.isDueToday(now, calendar))
            assertFalse(followUp.needsAttention(now, calendar))
        }
    }

    // needingAttention

    /** needingAttention orders overdue first, then priority, then earliest due */
    @Test
    fun needingAttentionOrdering() {
        val store = TestStore()
        val overdueHigh = Make.followUp(store.context, title = "overdue high", due = TestDates.dayStart(-1), priority = Priority.HIGH)
        val overdueOld = Make.followUp(store.context, title = "overdue old", due = TestDates.dayStart(-5))
        val overdueRecent = Make.followUp(store.context, title = "overdue recent", due = TestDates.dayStart(-2))
        val todayHigh = Make.followUp(
            store.context,
            title = "today high",
            due = TestDates.time(15),
            hasTime = true,
            priority = Priority.HIGH,
        )
        val todayNormal = Make.followUp(store.context, title = "today normal", due = TestDates.time(18), hasTime = true)
        val tomorrow = Make.followUp(store.context, title = "tomorrow", due = TestDates.dayStart(1))
        val completed = Make.followUp(store.context, title = "completed", due = TestDates.dayStart(-3), isCompleted = true)

        val result = FollowUpEngine.needingAttention(
            listOf(todayNormal, completed, overdueRecent, tomorrow, overdueHigh, todayHigh, overdueOld),
            now = now,
            calendar = calendar,
        )

        assertEquals(
            listOf("overdue high", "overdue old", "overdue recent", "today high", "today normal"),
            result.map { it.title },
        )
    }

    /** needingAttention is empty when nothing is due */
    @Test
    fun needingAttentionEmpty() {
        val store = TestStore()
        val future = Make.followUp(store.context, due = TestDates.dayStart(4))

        assertTrue(FollowUpEngine.needingAttention(listOf(future), now = now, calendar = calendar).isEmpty())
    }

    // upcoming

    /** upcoming excludes today and anything past the window */
    @Test
    fun upcomingWindow() {
        val store = TestStore()
        val today = Make.followUp(store.context, title = "today", due = TestDates.dayStart(0))
        val todayEvening = Make.followUp(store.context, title = "today evening", due = TestDates.time(18), hasTime = true)
        val tomorrow = Make.followUp(store.context, title = "tomorrow", due = TestDates.dayStart(1))
        val edge = Make.followUp(store.context, title = "day 7", due = TestDates.dayStart(7))
        val past = Make.followUp(store.context, title = "day 8", due = TestDates.dayStart(8))
        val completed = Make.followUp(store.context, title = "completed", due = TestDates.dayStart(2), isCompleted = true)

        val result = FollowUpEngine.upcoming(
            listOf(past, edge, todayEvening, tomorrow, completed, today),
            withinDays = 7,
            now = now,
            calendar = calendar,
        )

        assertEquals(listOf("tomorrow", "day 7"), result.map { it.title })
    }

    // bucket

    /** bucket maps a follow-up to its section */
    @Test
    fun bucketMapping() {
        val store = TestStore()
        val overdue = Make.followUp(store.context, due = TestDates.dayStart(-1))
        val today = Make.followUp(store.context, due = TestDates.dayStart(0))
        val thisWeek = Make.followUp(store.context, due = TestDates.dayStart(3))
        val weekEdge = Make.followUp(store.context, due = TestDates.dayStart(7))
        val later = Make.followUp(store.context, due = TestDates.dayStart(10))
        // Completed wins over overdue.
        val completed = Make.followUp(store.context, due = TestDates.dayStart(-9), isCompleted = true)

        assertEquals(FollowUpBucket.OVERDUE, FollowUpEngine.bucket(overdue, now, calendar))
        assertEquals(FollowUpBucket.TODAY, FollowUpEngine.bucket(today, now, calendar))
        assertEquals(FollowUpBucket.THIS_WEEK, FollowUpEngine.bucket(thisWeek, now, calendar))
        assertEquals(FollowUpBucket.THIS_WEEK, FollowUpEngine.bucket(weekEdge, now, calendar))
        assertEquals(FollowUpBucket.LATER, FollowUpEngine.bucket(later, now, calendar))
        assertEquals(FollowUpBucket.COMPLETED, FollowUpEngine.bucket(completed, now, calendar))
    }

    // grouped

    /** grouped keeps bucket order and drops empty buckets */
    @Test
    fun groupedOrderAndEmptyBuckets() {
        val store = TestStore()
        val overdue = Make.followUp(store.context, due = TestDates.dayStart(-2))
        val today = Make.followUp(store.context, due = TestDates.dayStart(0))
        val later = Make.followUp(store.context, due = TestDates.dayStart(21))
        val completed = Make.followUp(
            store.context,
            due = TestDates.dayStart(-4),
            isCompleted = true,
            completedAt = TestDates.days(-3),
        )

        // No THIS_WEEK item here, so that section must not appear.
        val groups = FollowUpEngine.grouped(listOf(later, completed, today, overdue), now = now, calendar = calendar)

        assertEquals(
            listOf(FollowUpBucket.OVERDUE, FollowUpBucket.TODAY, FollowUpBucket.LATER, FollowUpBucket.COMPLETED),
            groups.map { it.bucket },
        )
        assertTrue(groups.all { it.items.isNotEmpty() })
    }

    /** grouped sorts open items by due date then priority */
    @Test
    fun groupedSortsOpenItems() {
        val store = TestStore()
        val early = Make.followUp(store.context, title = "early", due = TestDates.dayStart(-4))
        val lateNormal = Make.followUp(store.context, title = "late normal", due = TestDates.dayStart(-1))
        val lateHigh = Make.followUp(store.context, title = "late high", due = TestDates.dayStart(-1), priority = Priority.HIGH)

        val groups = FollowUpEngine.grouped(listOf(lateNormal, lateHigh, early), now = now, calendar = calendar)
        val overdue = assertNotNull(groups.firstOrNull())

        assertEquals(FollowUpBucket.OVERDUE, overdue.bucket)
        assertEquals(listOf("early", "late high", "late normal"), overdue.items.map { it.title })
    }

    /** grouped applies completedLimit, keeping the most recently completed */
    @Test
    fun groupedAppliesCompletedLimit() {
        val store = TestStore()
        val newest = Make.followUp(
            store.context,
            title = "newest",
            due = TestDates.dayStart(-1),
            isCompleted = true,
            completedAt = TestDates.days(-1),
        )
        val middle = Make.followUp(
            store.context,
            title = "middle",
            due = TestDates.dayStart(-2),
            isCompleted = true,
            completedAt = TestDates.days(-2),
        )
        val oldest = Make.followUp(
            store.context,
            title = "oldest",
            due = TestDates.dayStart(-3),
            isCompleted = true,
            completedAt = TestDates.days(-3),
        )

        val groups = FollowUpEngine.grouped(
            listOf(middle, oldest, newest),
            now = now,
            calendar = calendar,
            completedLimit = 2,
        )
        val completed = assertNotNull(groups.firstOrNull())

        assertEquals(FollowUpBucket.COMPLETED, completed.bucket)
        assertEquals(listOf("newest", "middle"), completed.items.map { it.title })
    }

    // snooze

    /** snooze preserves time-of-day when hasTime is true */
    @Test
    fun snoozePreservesTime() {
        val store = TestStore()
        val followUp = Make.followUp(store.context, due = TestDates.time(15), hasTime = true)

        followUp.snooze(byDays = 2, from = now, calendar = calendar)

        assertEquals(TestDates.time(15, dayOffset = 2), followUp.dueDate)
        assertEquals(now, followUp.lastSnoozedAt)
    }

    /** snooze snaps to start of day when hasTime is false */
    @Test
    fun snoozeSnapsToStartOfDay() {
        val store = TestStore()
        val followUp = Make.followUp(store.context, due = TestDates.dayStart(1))

        followUp.snooze(byDays = 3, from = now, calendar = calendar)

        assertEquals(TestDates.dayStart(4), followUp.dueDate)
    }

    /** Snoozing an overdue follow-up moves it relative to now, not its old due date */
    @Test
    fun snoozeOverdueMovesFromNow() {
        val store = TestStore()
        val allDay = Make.followUp(store.context, due = TestDates.dayStart(-10))
        val timed = Make.followUp(store.context, due = TestDates.time(9, dayOffset = -10), hasTime = true)

        allDay.snooze(byDays = 1, from = now, calendar = calendar)
        timed.snooze(byDays = 1, from = now, calendar = calendar)

        assertEquals(TestDates.dayStart(1), allDay.dueDate)
        assertEquals(TestDates.days(1), timed.dueDate)
    }

    // DuePreset

    /** DuePreset.date returns start of day N days out */
    @Test
    fun duePresetDates() {
        for (preset in FollowUpEngine.DuePreset.entries) {
            assertEquals(TestDates.dayStart(preset.days), preset.date(from = now, calendar = calendar))
        }
        assertEquals(TestDates.dayStart(1), FollowUpEngine.DuePreset.TOMORROW.date(from = now, calendar = calendar))
        assertEquals(30, FollowUpEngine.DuePreset.NEXT_MONTH.days)
    }
}
