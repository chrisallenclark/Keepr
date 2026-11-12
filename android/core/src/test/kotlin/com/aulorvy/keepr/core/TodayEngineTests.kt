package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.domain.TodayEngine
import com.aulorvy.keepr.core.model.ContextMode
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.Priority
import com.aulorvy.keepr.core.model.RelationshipContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Translated from KeeprTests/TodayEngineTests.swift. Test names are the Swift
// function names; the Swift display name is in the comment above each.
class TodayEngineTests {

    private val now = TestDates.now
    private val calendar = TestDates.calendar

    // goingQuiet

    /** Thresholds differ per mode: 21 days for business, 60 for personal */
    @Test
    fun quietThresholds() {
        assertEquals(21, TodayEngine.quietThreshold(ContextMode.BUSINESS))
        assertEquals(60, TodayEngine.quietThreshold(ContextMode.PERSONAL))
    }

    /** Someone past the mode's threshold is going quiet; someone inside it is not */
    @Test
    fun goingQuietUsesThreshold() {
        val store = TestStore()
        val drifted = Make.person(
            store.context,
            given = "Dee",
            family = "Drifted",
            relationship = RelationshipContext.BOTH,
            lastInteractionAt = TestDates.days(-30),
        )
        val fresh = Make.person(
            store.context,
            given = "Fay",
            family = "Fresh",
            relationship = RelationshipContext.BOTH,
            lastInteractionAt = TestDates.days(-3),
        )

        val business = TodayEngine.goingQuiet(listOf(drifted, fresh), ContextMode.BUSINESS, now, calendar)
        assertEquals(listOf(drifted.id), business.map { it.id })

        // 30 days is still comfortably inside the 60-day personal threshold.
        val personal = TodayEngine.goingQuiet(listOf(drifted, fresh), ContextMode.PERSONAL, now, calendar)
        assertTrue(personal.isEmpty())
    }

    /** A person past the personal threshold is going quiet in personal mode */
    @Test
    fun goingQuietPersonalThreshold() {
        val store = TestStore()
        val veryQuiet = Make.person(
            store.context,
            given = "Vic",
            family = "Quiet",
            relationship = RelationshipContext.PERSONAL,
            lastInteractionAt = TestDates.days(-90),
        )

        val result = TodayEngine.goingQuiet(listOf(veryQuiet), ContextMode.PERSONAL, now, calendar)
        assertEquals(listOf(veryQuiet.id), result.map { it.id })
    }

    /** An open follow-up excludes someone from going quiet; a completed one does not */
    @Test
    fun goingQuietSkipsPeopleWithOpenFollowUps() {
        val store = TestStore()
        val planned = Make.person(
            store.context,
            given = "Pia",
            family = "Planned",
            relationship = RelationshipContext.BUSINESS,
            lastInteractionAt = TestDates.days(-40),
        )
        val done = Make.person(
            store.context,
            given = "Dan",
            family = "Done",
            relationship = RelationshipContext.BUSINESS,
            lastInteractionAt = TestDates.days(-40),
        )
        Make.followUp(store.context, due = TestDates.dayStart(2), person = planned)
        Make.followUp(store.context, due = TestDates.dayStart(-2), isCompleted = true, person = done)

        val result = TodayEngine.goingQuiet(listOf(planned, done), ContextMode.BUSINESS, now, calendar)
        assertEquals(listOf(done.id), result.map { it.id })
    }

    /** A never-contacted person surfaces only when flagged and created before the cutoff */
    @Test
    fun goingQuietNeverContacted() {
        val store = TestStore()
        val ordinary = Make.person(
            store.context,
            given = "Ora",
            family = "Ordinary",
            relationship = RelationshipContext.BUSINESS,
            createdAt = TestDates.days(-100),
        )
        val favoriteOld = Make.person(
            store.context,
            given = "Fay",
            family = "FavOld",
            relationship = RelationshipContext.BUSINESS,
            isFavorite = true,
            createdAt = TestDates.days(-100),
        )
        val favoriteNew = Make.person(
            store.context,
            given = "Fin",
            family = "FavNew",
            relationship = RelationshipContext.BUSINESS,
            isFavorite = true,
            createdAt = TestDates.days(-2),
        )
        val highPriorityOld = Make.person(
            store.context,
            given = "Hal",
            family = "HighOld",
            relationship = RelationshipContext.BUSINESS,
            priority = Priority.HIGH,
            createdAt = TestDates.days(-100),
        )

        val result = TodayEngine.goingQuiet(
            listOf(ordinary, favoriteOld, favoriteNew, highPriorityOld),
            ContextMode.BUSINESS,
            now,
            calendar,
        )

        val ids = result.map { it.id }.toSet()
        assertEquals(setOf(favoriteOld.id, highPriorityOld.id), ids)
    }

    /** goingQuiet sorts the longest-silent relationship first */
    @Test
    fun goingQuietOrdering() {
        val store = TestStore()
        val quietest = Make.person(
            store.context,
            given = "Qui",
            family = "Quietest",
            relationship = RelationshipContext.BUSINESS,
            lastInteractionAt = TestDates.days(-90),
        )
        val middle = Make.person(
            store.context,
            given = "Mid",
            family = "Middle",
            relationship = RelationshipContext.BUSINESS,
            lastInteractionAt = TestDates.days(-50),
        )
        val recent = Make.person(
            store.context,
            given = "Ron",
            family = "Recent",
            relationship = RelationshipContext.BUSINESS,
            lastInteractionAt = TestDates.days(-25),
        )

        val result = TodayEngine.goingQuiet(listOf(middle, recent, quietest), ContextMode.BUSINESS, now, calendar)
        assertEquals(listOf(quietest.id, middle.id, recent.id), result.map { it.id })
    }

    // recentlyActive

    /** recentlyActive returns the last 14 days, most recent first */
    @Test
    fun recentlyActiveDefaultWindow() {
        val store = TestStore()
        // Added long ago, so only the interactions decide who's recent.
        val added = TestDates.days(-100)
        val yesterday = Make.person(
            store.context,
            given = "Yara",
            family = "Yesterday",
            createdAt = added,
            lastInteractionAt = TestDates.days(-1),
        )
        val lastWeek = Make.person(
            store.context,
            given = "Lee",
            family = "LastWeek",
            createdAt = added,
            lastInteractionAt = TestDates.days(-6),
        )
        val outside = Make.person(
            store.context,
            given = "Oda",
            family = "Outside",
            createdAt = added,
            lastInteractionAt = TestDates.days(-20),
        )
        val never = Make.person(store.context, given = "Nev", family = "Never", createdAt = added)

        val result = TodayEngine.recentlyActive(
            listOf(outside, never, lastWeek, yesterday),
            now = now,
            calendar = calendar,
        )
        assertEquals(listOf(yesterday.id, lastWeek.id), result.map { it.id })
    }

    /** Someone added today is recent, even with nothing logged yet */
    @Test
    fun recentlyActiveIncludesNewPeople() {
        val store = TestStore()
        val justAdded = Make.person(
            store.context,
            given = "New",
            family = "Contact",
            createdAt = TestDates.days(-1),
        )
        val spokenTo = Make.person(
            store.context,
            given = "Old",
            family = "Friend",
            createdAt = TestDates.days(-200),
            lastInteractionAt = TestDates.days(-3),
        )

        val result = TodayEngine.recentlyActive(listOf(spokenTo, justAdded), now = now, calendar = calendar)

        assertEquals(
            listOf(justAdded.id, spokenTo.id),
            result.map { it.id },
            "categorizing three new contacts and seeing nothing appear reads as a bug",
        )
    }

    /** recentlyActive honours a custom window */
    @Test
    fun recentlyActiveCustomWindow() {
        val store = TestStore()
        val yesterday = Make.person(
            store.context,
            given = "Yara",
            family = "Yesterday",
            createdAt = TestDates.days(-100),
            lastInteractionAt = TestDates.days(-1),
        )
        val lastWeek = Make.person(
            store.context,
            given = "Lee",
            family = "LastWeek",
            createdAt = TestDates.days(-100),
            lastInteractionAt = TestDates.days(-6),
        )

        val result = TodayEngine.recentlyActive(
            listOf(lastWeek, yesterday),
            withinDays = 3,
            now = now,
            calendar = calendar,
        )
        assertEquals(listOf(yesterday.id), result.map { it.id })
    }

    // digest

    /** digest only considers people visible in the current mode */
    @Test
    fun digestRespectsMode() {
        val store = TestStore()
        val workPerson = Make.person(
            store.context,
            given = "Wes",
            family = "Work",
            relationship = RelationshipContext.BUSINESS,
        )
        val homePerson = Make.person(
            store.context,
            given = "Hal",
            family = "Home",
            relationship = RelationshipContext.PERSONAL,
        )
        Make.followUp(store.context, title = "work task", due = TestDates.dayStart(-1), person = workPerson)
        Make.followUp(store.context, title = "home task", due = TestDates.dayStart(-1), person = homePerson)

        val business = TodayEngine.digest(listOf(workPerson, homePerson), ContextMode.BUSINESS, now, calendar)
        assertEquals(listOf("work task"), business.needsAttention.map { it.title })

        val personal = TodayEngine.digest(listOf(workPerson, homePerson), ContextMode.PERSONAL, now, calendar)
        assertEquals(listOf("home task"), personal.needsAttention.map { it.title })
    }

    /** digest caps goingQuiet at goingQuietLimit */
    @Test
    fun digestGoingQuietLimit() {
        val store = TestStore()
        val people = mutableListOf<Person>()
        for (index in 0 until 7) {
            people.add(
                Make.person(
                    store.context,
                    given = "Q$index",
                    family = "Quiet$index",
                    relationship = RelationshipContext.BUSINESS,
                    lastInteractionAt = TestDates.days(-30 - index),
                ),
            )
        }

        val digest = TodayEngine.digest(people, ContextMode.BUSINESS, now, calendar)

        assertEquals(5, TodayEngine.goingQuietLimit)
        assertEquals(5, digest.goingQuiet.size)
        // The longest-silent person is the one with the largest negative offset.
        assertEquals(people[6].id, digest.goingQuiet.firstOrNull()?.id)
    }

    /** digest caps recent at recentLimit */
    @Test
    fun digestRecentLimit() {
        val store = TestStore()
        val people = mutableListOf<Person>()
        for (index in 0 until 8) {
            people.add(
                Make.person(
                    store.context,
                    given = "R$index",
                    family = "Recent$index",
                    relationship = RelationshipContext.PERSONAL,
                    createdAt = TestDates.days(-100),
                    lastInteractionAt = TestDates.days(-1 - index),
                ),
            )
        }

        val digest = TodayEngine.digest(people, ContextMode.PERSONAL, now, calendar)

        assertEquals(6, TodayEngine.recentLimit)
        assertEquals(6, digest.recent.size)
        assertEquals(people[0].id, digest.recent.firstOrNull()?.id)
    }

    /** digest caps upcoming at upcomingLimit and uses the 7-day window */
    @Test
    fun digestUpcomingLimit() {
        val store = TestStore()
        val person = Make.person(
            store.context,
            given = "Ula",
            family = "Upcoming",
            relationship = RelationshipContext.BUSINESS,
        )
        for (day in 1..7) {
            Make.followUp(store.context, title = "day $day", due = TestDates.dayStart(day), person = person)
        }

        val digest = TodayEngine.digest(listOf(person), ContextMode.BUSINESS, now, calendar)

        assertEquals(7, TodayEngine.upcomingWindowDays)
        assertEquals(5, TodayEngine.upcomingLimit)
        assertEquals(listOf("day 1", "day 2", "day 3", "day 4", "day 5"), digest.upcoming.map { it.title })
    }

    /** isCaughtUp is true exactly when needsAttention is empty */
    @Test
    fun digestIsCaughtUp() {
        val store = TestStore()
        val person = Make.person(
            store.context,
            given = "Cal",
            family = "Calm",
            relationship = RelationshipContext.BUSINESS,
        )
        Make.followUp(store.context, title = "later", due = TestDates.dayStart(3), person = person)

        val calm = TodayEngine.digest(listOf(person), ContextMode.BUSINESS, now, calendar)
        assertTrue(calm.needsAttention.isEmpty())
        assertTrue(calm.isCaughtUp)
        assertFalse(calm.isEmpty)

        Make.followUp(store.context, title = "overdue", due = TestDates.dayStart(-1), person = person)

        val busy = TodayEngine.digest(listOf(person), ContextMode.BUSINESS, now, calendar)
        assertEquals(1, busy.needsAttention.size)
        assertFalse(busy.isCaughtUp)
    }

    /** An empty digest is both empty and caught up */
    @Test
    fun digestEmpty() {
        val digest = TodayEngine.digest(emptyList(), ContextMode.BUSINESS, now, calendar)
        assertTrue(digest.isEmpty)
        assertTrue(digest.isCaughtUp)
    }
}
