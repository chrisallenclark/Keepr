package com.aulorvy.keepr.notifications

import com.aulorvy.keepr.core.domain.CadenceNotificationScheduler
import com.aulorvy.keepr.core.model.FollowUp
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.services.FollowUpScheduler
import com.aulorvy.keepr.ui.screens.AppState

/**
 * Rhythm notifications are worked out in advance, so iOS rebuilds them every
 * time Today appears. Android does the same, plus on every launch and return
 * to the foreground. Must run on the main thread (it reads the model).
 */
fun AppState.rescheduleRhythmReminders() {
    CadenceNotificationScheduler.sync(
        people = context.fetch<Person>().filter { !it.isDeleted },
        service = notifications,
        remindersEnabled = remindersEnabled,
        now = now(),
        calendar = calendar,
        defaults = repository.preferences,
        scope = scope,
    )
}

/**
 * Everything at once: rhythms, and every open follow-up whose reminder is
 * still ahead. Follow-up reminders are normally scheduled when they're
 * edited; doing it again here picks up any that were skipped because
 * notifications weren't allowed at the time. Past ones are left alone, so a
 * reminder already showing isn't withdrawn just because the app opened.
 */
fun AppState.rescheduleAllReminders() {
    rescheduleRhythmReminders()
    if (!remindersEnabled) return
    val current = now()
    context.fetch<FollowUp>()
        .filter { !it.isDeleted && !it.isCompleted }
        .forEach { followUp ->
            val reminder = FollowUpScheduler.reminder(followUp, calendar) ?: return@forEach
            if (reminder.fireDate.isAfter(current)) {
                FollowUpScheduler.sync(followUp, notifications, remindersEnabled, scope)
            }
        }
}
