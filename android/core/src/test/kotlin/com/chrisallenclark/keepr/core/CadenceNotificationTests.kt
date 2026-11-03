package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.CadenceNotificationScheduler
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.services.LocalReminder
import com.chrisallenclark.keepr.core.services.StubNotificationService
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Translated from KeeprTests/CadenceNotificationTests.swift ("Rhythm notifications").
//
// Notifications are the one part of this app that can interrupt someone's day,
// so the rules that keep them rare matter more than the ones that make them fire.
class CadenceNotificationTests {

    private fun client(store: TestStore, name: String, lastInteraction: Instant?, tag: RelationshipTag): Person {
        val person = Make.person(
            store.context,
            given = name,
            family = "Client",
            relationship = RelationshipContext.BUSINESS,
            createdAt = TestDates.days(-200),
            lastInteractionAt = lastInteraction,
        )
        Make.attach(tag, to = person)
        return person
    }

    private fun tag(store: TestStore, days: Int): RelationshipTag {
        val tag = Make.tag(store.context, name = "Current Client")
        tag.cadenceDays = days
        return tag
    }

    private fun build(people: List<Person>, lastBacklogNudge: Instant? = null): List<LocalReminder> =
        CadenceNotificationScheduler.reminders(
            people,
            now = TestDates.now,
            calendar = TestDates.calendar,
            lastBacklogNudge = lastBacklogNudge,
        )

    /** One person due names them, so the notification is worth reading */
    @Test
    fun singlePersonIsNamed() {
        val store = TestStore()
        val clients = tag(store, days = 30)
        // Spoke 20 days ago, so due in 10.
        val person = client(store, "Stanley", lastInteraction = TestDates.days(-20), tag = clients)

        val reminders = build(listOf(person))

        val reminder = assertNotNull(reminders.firstOrNull())
        assertEquals("Reach out to Stanley Client", reminder.title)
        assertTrue(reminder.body.contains("Last spoke"))
    }

    /** Several due the same day become one notification with the count */
    @Test
    fun sameDayBecomesOneNotification() {
        val store = TestStore()
        val clients = tag(store, days = 30)
        val people = listOf("Ann", "Ben", "Cara", "Dev").map {
            client(store, it, lastInteraction = TestDates.days(-20), tag = clients)
        }

        val reminders = build(people)

        assertEquals(1, reminders.size, "one buzz, not four")
        val reminder = assertNotNull(reminders.firstOrNull())
        assertEquals("4 people to reach out to", reminder.title)
        assertTrue(reminder.body.contains("and 1 more"), "a few names, then a count")
    }

    /** Different days get their own notification */
    @Test
    fun differentDaysAreSeparate() {
        val store = TestStore()
        val clients = tag(store, days = 30)
        val soon = client(store, "Soon", lastInteraction = TestDates.days(-25), tag = clients)
        val later = client(store, "Later", lastInteraction = TestDates.days(-20), tag = clients)

        val reminders = build(listOf(soon, later))

        assertEquals(2, reminders.size)
        assertTrue(reminders[0].fireDate < reminders[1].fireDate)
    }

    /** Notifications land in the morning, not at midnight */
    @Test
    fun firesInTheMorning() {
        val store = TestStore()
        val clients = tag(store, days = 30)
        val person = client(store, "Stanley", lastInteraction = TestDates.days(-20), tag = clients)

        val reminder = assertNotNull(build(listOf(person)).firstOrNull())

        val hour = TestDates.calendar.hour(reminder.fireDate)
        assertEquals(CadenceNotificationScheduler.HOUR, hour)
    }

    /** People already overdue are raised once, together */
    @Test
    fun backlogIsOneNotification() {
        val store = TestStore()
        val clients = tag(store, days = 30)
        val people = listOf("Ann", "Ben").map {
            client(store, it, lastInteraction = TestDates.days(-90), tag = clients)
        }

        val reminders = build(people)

        assertEquals(1, reminders.size)
        assertEquals(true, reminders.firstOrNull()?.identifier?.endsWith("backlog"))
        assertEquals("2 people to reach out to", reminders.firstOrNull()?.title)
    }

    /** The backlog isn't raised again the next morning */
    @Test
    fun backlogIsNotADailyDrumbeat() {
        val store = TestStore()
        val clients = tag(store, days = 30)
        val person = client(store, "Ann", lastInteraction = TestDates.days(-90), tag = clients)

        // Yesterday's nudge already went out.
        val reminders = build(listOf(person), lastBacklogNudge = TestDates.days(-1))
        val backlog = assertNotNull(reminders.firstOrNull { it.identifier.endsWith("backlog") })

        val daysOut = TestDates.calendar.dayStartsBetween(TestDates.now, backlog.fireDate)
        assertTrue(daysOut >= CadenceNotificationScheduler.BACKLOG_INTERVAL - 1)
    }

    /** Someone with a plan already isn't notified about twice */
    @Test
    fun openFollowUpSuppressesIt() {
        val store = TestStore()
        val clients = tag(store, days = 30)
        val person = client(store, "Stanley", lastInteraction = TestDates.days(-20), tag = clients)
        Make.followUp(store.context, due = TestDates.days(3), person = person)

        assertTrue(build(listOf(person)).isEmpty())
    }

    /** No rhythm means no notifications at all */
    @Test
    fun silentWithoutCadence() {
        val store = TestStore()
        val person = Make.person(
            store.context,
            given = "Nobody",
            family = "Special",
            relationship = RelationshipContext.BUSINESS,
            lastInteractionAt = TestDates.days(-200),
        )

        assertTrue(build(listOf(person)).isEmpty())
    }

    /** Turning reminders off clears the schedule rather than rebuilding it */
    @Test
    fun disabledMeansNothingScheduled() = runTest {
        val store = TestStore()
        val clients = tag(store, days = 30)
        val person = client(store, "Stanley", lastInteraction = TestDates.days(-20), tag = clients)
        val service = StubNotificationService()

        CadenceNotificationScheduler.sync(
            people = listOf(person),
            service = service,
            remindersEnabled = false,
            now = TestDates.now,
            calendar = TestDates.calendar,
            defaults = store.defaults,
            scope = this,
        )

        assertTrue(service.reminders.isEmpty())
    }
}
