package com.chrisallenclark.keepr.core.services

import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.KeeprCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant

// Translated from the platform-independent parts of
// Keepr/Services/NotificationService.swift. The live scheduler
// (UNUserNotificationCenter on iOS) is the Android app's to implement, over
// AlarmManager, against [NotificationScheduling].

/** A value-type description of a follow-up reminder, so no record crosses a thread. */
data class FollowUpReminder(
    val identifier: String,
    val personName: String,
    val title: String,
    val fireDate: Instant,
) {
    /**
     * How the live service presents it: the person's name and the user's own
     * words — nothing generated. Seeing who it's about is the whole value.
     */
    fun toLocalReminder(): LocalReminder =
        LocalReminder(identifier = identifier, title = personName, body = title, fireDate = fireDate)

    companion object {
        /** All-day follow-ups fire at 9am rather than midnight. */
        const val DEFAULT_HOUR = 9
    }
}

/**
 * Any local notification this app sends: a title, a body, a moment.
 *
 * Follow-ups and rhythms both end up here. Nothing runs at delivery time, so
 * everything is worked out in advance and rescheduled whenever the app is
 * open — which is why the content is plain data. [fireDate] is the exact
 * moment (iOS triggers on its year/month/day/hour/minute in the current zone).
 */
data class LocalReminder(
    val identifier: String,
    val title: String,
    val body: String,
    val fireDate: Instant,
)

/** `UNAuthorizationStatus`, with its raw values. */
enum class NotificationAuthorizationStatus(val rawValue: Int) {
    NOT_DETERMINED(0),
    DENIED(1),
    AUTHORIZED(2),
    PROVISIONAL(3),
    EPHEMERAL(4);

    companion object {
        fun from(raw: Int?): NotificationAuthorizationStatus? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** What the logic needs from the platform's notification scheduler. */
interface NotificationScheduling {
    suspend fun authorizationStatus(): NotificationAuthorizationStatus

    /** Presents the system prompt. Returns whether it was granted. */
    suspend fun requestAuthorization(): Boolean

    /**
     * Schedules (or replaces) the reminder. Past dates are ignored. The iOS
     * service presents it as [FollowUpReminder.toLocalReminder].
     */
    suspend fun schedule(reminder: FollowUpReminder)

    /**
     * Schedules (or replaces) the reminder. The iOS service cancels the
     * identifier first, then skips past dates and unauthorized states.
     */
    suspend fun scheduleReminder(reminder: LocalReminder)

    /** Removes the pending reminder and any delivered one with this identifier. */
    suspend fun cancel(identifier: String)

    /**
     * Clears a whole family of pending reminders — used to rebuild the rhythm
     * schedule from scratch every time something changes.
     */
    suspend fun cancelReminders(withPrefix: String)

    suspend fun cancelAll()
}

// Preview / test double

/** Records what it was asked to do; schedules nothing. */
class StubNotificationService : NotificationScheduling {
    var status: NotificationAuthorizationStatus = NotificationAuthorizationStatus.AUTHORIZED

    private val scheduledStore = mutableListOf<FollowUpReminder>()
    private val reminderStore = mutableListOf<LocalReminder>()
    private val cancelledStore = mutableListOf<String>()

    val scheduled: List<FollowUpReminder> get() = scheduledStore.toList()
    val reminders: List<LocalReminder> get() = reminderStore.toList()
    val cancelled: List<String> get() = cancelledStore.toList()

    override suspend fun authorizationStatus(): NotificationAuthorizationStatus = status
    override suspend fun requestAuthorization(): Boolean = status == NotificationAuthorizationStatus.AUTHORIZED

    override suspend fun schedule(reminder: FollowUpReminder) {
        scheduledStore.removeAll { it.identifier == reminder.identifier }
        scheduledStore.add(reminder)
    }

    override suspend fun scheduleReminder(reminder: LocalReminder) {
        reminderStore.removeAll { it.identifier == reminder.identifier }
        reminderStore.add(reminder)
    }

    override suspend fun cancel(identifier: String) {
        cancelledStore.add(identifier)
        scheduledStore.removeAll { it.identifier == identifier }
        reminderStore.removeAll { it.identifier == identifier }
    }

    override suspend fun cancelReminders(withPrefix: String) {
        val matching = reminderStore.map { it.identifier }.filter { it.startsWith(withPrefix) }
        cancelledStore.addAll(matching)
        reminderStore.removeAll { it.identifier.startsWith(withPrefix) }
    }

    override suspend fun cancelAll() {
        cancelledStore.addAll(scheduledStore.map { it.identifier })
        cancelledStore.addAll(reminderStore.map { it.identifier })
        scheduledStore.clear()
        reminderStore.clear()
    }
}

// Model bridge

/**
 * Turns [FollowUp] records into reminders and keeps the two in sync.
 *
 * iOS fires the service calls in an unstructured `Task` and returns at once;
 * here they're launched in the caller's [CoroutineScope] (the UI's main
 * scope), and the returned [Job] can be joined when the result matters.
 */
object FollowUpScheduler {

    fun reminder(followUp: FollowUp, calendar: KeeprCalendar = KeeprCalendar.current()): FollowUpReminder? {
        if (followUp.isCompleted) return null
        val person = followUp.person ?: return null

        val fireDate = if (followUp.hasTime) {
            followUp.dueDate
        } else {
            calendar.settingTime(FollowUpReminder.DEFAULT_HOUR, 0, 0, followUp.dueDate)
        }

        return FollowUpReminder(
            identifier = identifier(followUp),
            personName = person.displayName,
            title = followUp.title,
            fireDate = fireDate,
        )
    }

    /** `"followup-<UUID>"`, uppercase like Foundation's `uuidString`. */
    fun identifier(followUp: FollowUp): String = "followup-${followUp.id.toString().uppercase()}"

    /** Call after any change to a follow-up's due date, title or completion. */
    fun sync(
        followUp: FollowUp,
        service: NotificationScheduling,
        remindersEnabled: Boolean,
        scope: CoroutineScope,
    ): Job {
        val identifier = identifier(followUp)
        followUp.notificationIdentifier = identifier

        val reminder = if (remindersEnabled) reminder(followUp) else null
        if (reminder == null) {
            return scope.launch { service.cancel(identifier) }
        }
        return scope.launch { service.schedule(reminder) }
    }

    fun cancel(followUp: FollowUp, service: NotificationScheduling, scope: CoroutineScope): Job {
        val identifier = followUp.notificationIdentifier ?: identifier(followUp)
        return scope.launch { service.cancel(identifier) }
    }
}
