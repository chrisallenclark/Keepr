package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.CadenceEngine
import com.chrisallenclark.keepr.core.domain.TodayEngine
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import com.chrisallenclark.keepr.core.model.RelationshipTag
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Translated from KeeprTests/CadenceEngineTests.swift. Test names are the Swift
// function names; the Swift display name is in the comment above each.
//
// Cadence is the app telling you who to call today, so it has to be right in
// both directions: never silent about someone genuinely overdue, and never
// noisy about someone you didn't ask to be chased about.
class CadenceEngineTests {

    private fun client(
        store: TestStore,
        name: String = "Client",
        lastInteraction: Instant?,
        createdAt: Instant = TestDates.days(-365),
        tags: List<RelationshipTag> = emptyList(),
    ): Person {
        val person = Make.person(
            store.context,
            given = name,
            family = "Person",
            relationship = RelationshipContext.BUSINESS,
            createdAt = createdAt,
            lastInteractionAt = lastInteraction,
        )
        for (tag in tags) Make.attach(tag, to = person)
        return person
    }

    private fun tag(store: TestStore, name: String, days: Int?): RelationshipTag {
        val tag = Make.tag(store.context, name = name)
        tag.cadenceDays = days
        return tag
    }

    // Where the interval comes from

    /** With no rhythm anywhere, the app says nothing */
    @Test
    fun silentByDefault() {
        val store = TestStore()
        val person = client(store, lastInteraction = TestDates.days(-400))

        assertNull(CadenceEngine.cadence(person))
        assertNull(CadenceEngine.status(person, now = TestDates.now))
        assertTrue(CadenceEngine.due(listOf(person), now = TestDates.now).isEmpty())
    }

    /** A type's rhythm applies to everyone with that type */
    @Test
    fun typeCadenceApplies() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(store, lastInteraction = TestDates.days(-31), tags = listOf(clients))

        val cadence = assertNotNull(CadenceEngine.cadence(person))
        assertEquals(30, cadence.days)
        assertEquals("Current Client", cadence.source)
    }

    /** Someone with two types is held to the shorter promise */
    @Test
    fun shortestCadenceWins() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val friends = tag(store, "Friend", days = 90)
        val person = client(store, lastInteraction = TestDates.days(-40), tags = listOf(clients, friends))

        assertEquals(30, CadenceEngine.cadence(person)?.days)
    }

    /** A person's own setting overrides their types */
    @Test
    fun personOverridesType() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(store, lastInteraction = TestDates.days(-10), tags = listOf(clients))
        person.cadenceDays = 7

        val cadence = assertNotNull(CadenceEngine.cadence(person))
        assertEquals(7, cadence.days)
        assertNull(cadence.source, "their own setting has no type to attribute it to")
    }

    /** Zero means never chase me about this one */
    @Test
    fun zeroMeansNever() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(store, lastInteraction = TestDates.days(-400), tags = listOf(clients))
        person.cadenceDays = 0

        assertNull(CadenceEngine.cadence(person))
        assertTrue(CadenceEngine.due(listOf(person), now = TestDates.now).isEmpty())
    }

    // Being due

    /** The clock runs from the last interaction */
    @Test
    fun dueFromLastInteraction() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(store, lastInteraction = TestDates.days(-45), tags = listOf(clients))

        val status = assertNotNull(CadenceEngine.status(person, now = TestDates.now, calendar = TestDates.calendar))
        assertEquals(15, status.daysOverdue)
        assertTrue(status.isOverdue)
        assertEquals("15 days over", status.summary)
    }

    /** Inside the interval, nobody is chased */
    @Test
    fun notYetDue() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(store, lastInteraction = TestDates.days(-10), tags = listOf(clients))

        val status = assertNotNull(CadenceEngine.status(person, now = TestDates.now, calendar = TestDates.calendar))
        assertEquals(false, status.isOverdue)
        assertEquals("In 20 days", status.summary)
        assertTrue(CadenceEngine.due(listOf(person), now = TestDates.now, calendar = TestDates.calendar).isEmpty())
    }

    /** A client imported today isn't instantly overdue */
    @Test
    fun newPeopleGetTheFullInterval() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(
            store,
            lastInteraction = null,
            createdAt = TestDates.now,
            tags = listOf(clients),
        )

        val status = assertNotNull(CadenceEngine.status(person, now = TestDates.now, calendar = TestDates.calendar))
        assertEquals(false, status.isOverdue)
        assertTrue(CadenceEngine.due(listOf(person), now = TestDates.now, calendar = TestDates.calendar).isEmpty())
    }

    /** Never contacted and long since added is overdue */
    @Test
    fun neverContactedEventuallyComesDue() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(
            store,
            lastInteraction = null,
            createdAt = TestDates.days(-60),
            tags = listOf(clients),
        )

        assertEquals(
            true,
            CadenceEngine.status(person, now = TestDates.now, calendar = TestDates.calendar)?.isOverdue,
        )
    }

    /** Logging anything resets the clock, with no extra step */
    @Test
    fun loggingResetsTheClock() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(store, lastInteraction = TestDates.days(-45), tags = listOf(clients))
        assertEquals(1, CadenceEngine.due(listOf(person), now = TestDates.now, calendar = TestDates.calendar).size)

        person.lastInteractionAt = TestDates.now

        assertTrue(CadenceEngine.due(listOf(person), now = TestDates.now, calendar = TestDates.calendar).isEmpty())
    }

    /** Someone with a plan already isn't chased twice */
    @Test
    fun openFollowUpSuppressesTheNudge() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(store, lastInteraction = TestDates.days(-45), tags = listOf(clients))
        Make.followUp(store.context, due = TestDates.days(2), person = person)

        assertTrue(CadenceEngine.due(listOf(person), now = TestDates.now, calendar = TestDates.calendar).isEmpty())
    }

    /** Archived people are left alone */
    @Test
    fun archivedPeopleAreNotChased() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(store, lastInteraction = TestDates.days(-90), tags = listOf(clients))
        person.status = RelationshipStatus.ARCHIVED

        assertNull(CadenceEngine.status(person, now = TestDates.now))
    }

    /** The most overdue person is at the top */
    @Test
    fun mostOverdueFirst() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val slightly = client(store, name = "Slightly", lastInteraction = TestDates.days(-35), tags = listOf(clients))
        val badly = client(store, name = "Badly", lastInteraction = TestDates.days(-120), tags = listOf(clients))

        val due = CadenceEngine.due(listOf(slightly, badly), now = TestDates.now, calendar = TestDates.calendar)

        assertEquals(listOf("Badly", "Slightly"), due.map { it.person.givenName })
    }

    // Wording

    /** Intervals are written the way people say them */
    @Test
    fun intervalsReadNaturally() {
        assertEquals("Every week", CadenceEngine.label(forDays = 7))
        assertEquals("Every month", CadenceEngine.label(forDays = 30))
        assertEquals("Every 3 months", CadenceEngine.label(forDays = 90))
        assertEquals("Every 45 days", CadenceEngine.label(forDays = 45))
    }

    // Today

    /** Today lists the people a rhythm says are due */
    @Test
    fun digestSurfacesThem() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(store, lastInteraction = TestDates.days(-45), tags = listOf(clients))

        val digest = TodayEngine.digest(listOf(person), ContextMode.BUSINESS, TestDates.now, TestDates.calendar)

        assertEquals(listOf(person.id), digest.dueForContact.map { it.person.id })
        assertEquals(false, digest.isCaughtUp)
    }

    /** A rhythm replaces the blanket going-quiet rule rather than doubling it */
    @Test
    fun cadenceSuppressesGoingQuiet() {
        val store = TestStore()
        val clients = tag(store, "Current Client", days = 30)
        val person = client(store, lastInteraction = TestDates.days(-45), tags = listOf(clients))

        val digest = TodayEngine.digest(listOf(person), ContextMode.BUSINESS, TestDates.now, TestDates.calendar)

        assertEquals(1, digest.dueForContact.size)
        assertTrue(digest.goingQuiet.isEmpty(), "one nudge per person, not two")
    }

    /** People with no rhythm still fall back to going quiet */
    @Test
    fun withoutCadenceTheOldRuleApplies() {
        val store = TestStore()
        val person = client(store, lastInteraction = TestDates.days(-45))

        val digest = TodayEngine.digest(listOf(person), ContextMode.BUSINESS, TestDates.now, TestDates.calendar)

        assertTrue(digest.dueForContact.isEmpty())
        assertEquals(listOf(person.id), digest.goingQuiet.map { it.id })
    }
}
