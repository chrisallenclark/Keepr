package com.chrisallenclark.keepr.core.domain

import com.chrisallenclark.keepr.core.model.KeeprCalendar
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import com.chrisallenclark.keepr.core.persistence.Preferences
import com.chrisallenclark.keepr.core.services.LocalReminder
import com.chrisallenclark.keepr.core.services.NotificationScheduling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant

// Translated from Keepr/Domain/CadenceNotificationScheduler.swift.

/**
 * Turns rhythms into notifications, one per day rather than one per person.
 *
 * Nothing runs when a notification is due, so everything is worked out ahead
 * and the whole schedule is rebuilt whenever the app is open — fine, because a
 * rhythm's due date only moves when you log something, in the app.
 *
 * Two rules keep this from becoming a nag:
 * - **One notification per day.** Four clients due Thursday is one alert
 *   saying four, not four alerts.
 * - **The standing backlog is raised weekly, not daily**, or people already
 *   past their rhythm would generate the same alert every morning forever.
 */
object CadenceNotificationScheduler {

    const val IDENTIFIER_PREFIX = "cadence-"
    /** Morning, not midnight — a notification you see when you might act on it. */
    const val HOUR = 9
    /** How far ahead to schedule. iOS caps pending notifications at 64. */
    const val HORIZON_DAYS = 45
    /** How often the already-overdue pile is worth raising again. */
    const val BACKLOG_INTERVAL = 7

    private const val LAST_BACKLOG_KEY = "keepr.lastCadenceBacklogNudge"

    /**
     * Builds the reminders for the next few weeks. Anyone already overdue
     * lands in one "backlog" reminder, scheduled a week after the last one so
     * it can't turn into a daily drumbeat.
     */
    fun reminders(
        people: List<Person>,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
        lastBacklogNudge: Instant? = null,
    ): List<LocalReminder> {
        val active = people.filter { it.status == RelationshipStatus.ACTIVE && it.openFollowUps.isEmpty() }

        val byDay = mutableMapOf<Instant, MutableList<Person>>()
        val overdue = mutableListOf<Person>()

        for (person in active) {
            val status = CadenceEngine.status(person, now, calendar) ?: continue
            if (status.isOverdue) {
                overdue.add(person)
                continue
            }
            val fire = fireDate(status.dueDate, calendar)
            val horizon = calendar.addingDays(HORIZON_DAYS, now)
            if (!(fire > now && fire <= horizon)) continue
            byDay.getOrPut(calendar.startOfDay(fire)) { mutableListOf() }.add(person)
        }

        val result = byDay.entries
            .sortedBy { it.key }
            .mapNotNull { (day, dayPeople) ->
                reminder(dayPeople, fireDate(day, calendar), identifier(day, calendar))
            }
            .toMutableList()

        if (overdue.isNotEmpty()) {
            val fire = backlogFireDate(now, calendar, lastBacklogNudge)
            reminder(overdue, fire, "backlog")?.let { result.add(it) }
        }
        return result
    }

    /**
     * Rebuilds the whole schedule. Cheap enough to run on every launch.
     *
     * The backlog bookkeeping is written before returning; the service calls
     * run in [scope], as iOS runs them in an unstructured `Task`.
     */
    fun sync(
        people: List<Person>,
        service: NotificationScheduling,
        remindersEnabled: Boolean,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
        defaults: Preferences,
        scope: CoroutineScope,
    ): Job {
        val lastNudge = defaults.string(LAST_BACKLOG_KEY)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val built = if (remindersEnabled) reminders(people, now, calendar, lastNudge) else emptyList()

        built.firstOrNull { it.identifier.endsWith("backlog") }?.let { backlog ->
            defaults.setString(LAST_BACKLOG_KEY, backlog.fireDate.toString())
        }

        return scope.launch {
            service.cancelReminders(IDENTIFIER_PREFIX)
            for (reminder in built) {
                service.scheduleReminder(reminder)
            }
        }
    }

    // Content

    /**
     * One name reads as a task; several read as a number, which is the honest
     * summary — with the first few names so it's still worth glancing at.
     */
    fun reminder(people: List<Person>, fireDate: Instant, identifierSuffix: String): LocalReminder? {
        if (people.isEmpty()) return null
        val names = people.map { it.displayName }.sortedWith(CadenceEngine.swiftOrder)

        val title: String
        val body: String

        if (names.size == 1) {
            title = "Reach out to ${names[0]}"
            body = lastContactPhrase(people.first())
        } else {
            title = "${names.size} people to reach out to"
            val shown = names.take(3).joinToString(", ")
            body = if (names.size > 3) "$shown and ${names.size - 3} more" else shown
        }

        return LocalReminder(
            identifier = IDENTIFIER_PREFIX + identifierSuffix,
            title = title,
            body = body,
            fireDate = fireDate,
        )
    }

    private fun lastContactPhrase(person: Person): String {
        val last = person.lastInteractionAt ?: return "You haven't logged anything with them yet."
        // Against the real clock and calendar, as on iOS.
        return "Last spoke ${RelativeDate.past(last).lowercase()}."
    }

    // Dates

    private fun fireDate(day: Instant, calendar: KeeprCalendar): Instant =
        calendar.settingTime(HOUR, 0, 0, day)

    /** Tomorrow morning, or a week after the last backlog nudge — whichever is later. */
    private fun backlogFireDate(now: Instant, calendar: KeeprCalendar, lastNudge: Instant?): Instant {
        val earliest = fireDate(calendar.addingDays(1, now), calendar)
        if (lastNudge == null) return earliest
        val next = calendar.addingDays(BACKLOG_INTERVAL, lastNudge)
        return if (next > earliest) next else earliest
    }

    /** "2025-7-5": unpadded year-month-day, as iOS writes it. */
    private fun identifier(day: Instant, calendar: KeeprCalendar): String =
        "${calendar.year(day)}-${calendar.month(day)}-${calendar.day(day)}"
}
