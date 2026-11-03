package com.chrisallenclark.keepr.ui.screens

import com.chrisallenclark.keepr.core.domain.Outreach
import com.chrisallenclark.keepr.core.domain.RelativeDate
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.services.FollowUpScheduler
import com.chrisallenclark.keepr.notifications.rescheduleRhythmReminders
import com.chrisallenclark.keepr.ui.components.FollowUpRow
import com.chrisallenclark.keepr.ui.components.Haptics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/*
 * The mutations the iOS screens perform, each one the same sequence of steps
 * as its Swift original: change the record, keep its reminder in step, save,
 * and confirm with the same haptic.
 */

/** Done ⇄ not done. iOS `FollowUpsView.toggle` / `TodayView.complete`. */
fun AppState.toggle(followUp: FollowUp, haptics: Haptics) {
    if (followUp.isCompleted) {
        followUp.reopen()
        FollowUpScheduler.sync(followUp, notifications, remindersEnabled, scope)
    } else {
        followUp.complete(now())
        FollowUpScheduler.cancel(followUp, notifications, scope)
    }
    save()
    haptics.success()
}

fun AppState.snooze(followUp: FollowUp, days: Int, haptics: Haptics) {
    followUp.snooze(days, now(), calendar)
    FollowUpScheduler.sync(followUp, notifications, remindersEnabled, scope)
    save()
    haptics.light()
}

fun AppState.delete(followUp: FollowUp) {
    FollowUpScheduler.cancel(followUp, notifications, scope)
    context.delete(followUp)
    save()
}

fun AppState.toggleFavorite(person: Person, haptics: Haptics) {
    person.isFavorite = !person.isFavorite
    person.touch(now())
    save()
    haptics.light()
}

fun AppState.markReachedOut(person: Person, haptics: Haptics) {
    Outreach.markReachedOut(person, at = now(), context = context)
    save()
    haptics.success()
    // The rhythm just reset, so its reminder moves (iOS TodayView.markReachedOut).
    rescheduleRhythmReminders()
}

fun AppState.markReplied(person: Person, haptics: Haptics) {
    Outreach.markReplied(person, now())
    save()
    haptics.light()
}

/** The due line under a follow-up: "Overdue · Yesterday", "Friday at 9:00 AM". */
fun AppState.dueText(followUp: FollowUp): String {
    if (followUp.isCompleted) return "Completed"
    val due = RelativeDate.dueWithTime(followUp, now(), calendar)
    return if (followUp.isOverdue(now(), calendar)) "Overdue · $due" else due
}

/** iOS `FollowUpRow`, bound to a record. */
@Composable
fun FollowUpRecordRow(
    state: AppState,
    followUp: FollowUp,
    haptics: Haptics,
    modifier: Modifier = Modifier,
    showsPerson: Boolean = true,
) {
    FollowUpRow(
        title = followUp.title,
        dueText = state.dueText(followUp),
        modifier = modifier,
        personName = if (showsPerson) followUp.person?.displayName else null,
        isCompleted = followUp.isCompleted,
        isOverdue = followUp.isOverdue(state.now(), state.calendar),
        isHighPriority = followUp.priority == com.chrisallenclark.keepr.core.model.Priority.HIGH,
        onComplete = { state.toggle(followUp, haptics) },
    )
}
