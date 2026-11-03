package com.chrisallenclark.keepr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.CadenceItem
import com.chrisallenclark.keepr.core.domain.Outreach
import com.chrisallenclark.keepr.core.domain.PeopleEngine
import com.chrisallenclark.keepr.core.domain.RelativeDate
import com.chrisallenclark.keepr.core.domain.TodayEngine
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.ui.components.ContextSwitcher
import com.chrisallenclark.keepr.ui.components.EmptyState
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.GroupedSectionScope
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.KeeprButtonStyle
import com.chrisallenclark.keepr.ui.components.KeeprTopBar
import com.chrisallenclark.keepr.ui.components.ListMetrics
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.PersonCompactRow
import com.chrisallenclark.keepr.ui.components.SwipeAction
import com.chrisallenclark.keepr.ui.components.SwipeActionsRow
import com.chrisallenclark.keepr.ui.components.TabBarClearance
import com.chrisallenclark.keepr.ui.components.ToolbarButton
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.medium
import com.chrisallenclark.keepr.ui.components.Haptics

/** Where a compact person row's text starts: past the 36pt avatar and a 12pt gap. */
internal val compactTextStart = ListMetrics.rowHorizontal + 36.dp + Spacing.medium

/**
 * What needs attention right now. Translated from
 * Keepr/Features/Today/TodayView.swift.
 */
@Composable
fun TodayScreen(
    mode: ModeBinding,
    onOpenPerson: (Person) -> Unit,
    onOpenSettings: () -> Unit,
    onQuickCapture: () -> Unit,
) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    observeStore().value
    val now = state.now()
    val people = state.context.fetch<Person>().sortedByDescending { it.updatedAt }
    val digest = TodayEngine.digest(people, mode.mode, now, state.calendar)
    val waiting = Outreach.waiting(PeopleEngine.visible(people, mode.mode), now, state.calendar)
    val colors = KeeprTheme.colors

    Column(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        KeeprTopBar(
            title = greeting(state.calendar.hour(now)),
            leading = { ToolbarButton("gearshape", "Settings", onOpenSettings) },
            trailing = { ToolbarButton("square.and.pencil", "Quick Capture", onQuickCapture) },
            pinned = { ContextSwitcher(mode.mode == ContextMode.PERSONAL, { mode.set(if (it) ContextMode.PERSONAL else ContextMode.BUSINESS) }) },
        )

        if (digest.isEmpty && waiting.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "You're caught up.",
                    symbolName = "checkmark.circle",
                    description = if (mode.mode == ContextMode.BUSINESS) "Nothing needs your attention in Business right now."
                    else "Nothing needs your attention in Personal right now.",
                    modifier = Modifier.padding(bottom = 60.dp),
                ) {
                    KeeprButton("Quick Capture", onQuickCapture, style = KeeprButtonStyle.Bordered)
                }
            }
            return@Column
        }

        Column(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(10.dp))
            val sections = mutableListOf<@Composable () -> Unit>()

            if (digest.needsAttention.isNotEmpty()) sections += { FollowUpSection("Needs Attention", digest.needsAttention, state, haptics, onOpenPerson) }
            if (waiting.isNotEmpty()) sections += {
                GroupedSection(header = "Waiting on a Reply", footer = "You reached out and haven't heard back. Swipe when they answer.") {
                    waiting.forEach { item ->
                        row(compactTextStart) {
                            SwipeActionsRow(trailing = listOf(SwipeAction("They Replied", "checkmark", colors.green) { state.markReplied(item.person, haptics) })) {
                                ListRow(onClick = { onOpenPerson(item.person) }) {
                                    PersonCompactRow(item.person.displayName, item.person.initials, Modifier.weight(1f), item.summary, item.person.photoData)
                                    SfSymbol("hourglass", colors.tertiaryLabel, KeeprTheme.typography.caption)
                                }
                            }
                        }
                    }
                }
            }
            if (digest.dueForContact.isNotEmpty()) sections += {
                GroupedSection(
                    header = "Time to Reach Out",
                    footer = "Based on how often you said these relationships are worth contacting. Logging anything resets the clock.",
                ) {
                    digest.dueForContact.forEach { item -> dueRow(item, state, haptics, onOpenPerson) }
                }
            }
            if (digest.upcoming.isNotEmpty()) sections += { FollowUpSection("Upcoming", digest.upcoming, state, haptics, onOpenPerson) }
            if (digest.goingQuiet.isNotEmpty()) sections += {
                GroupedSection(header = "Going Quiet", footer = "No interaction logged in a while, and nothing planned.") {
                    digest.goingQuiet.forEach { person ->
                        row(compactTextStart) {
                            ListRow(onClick = { onOpenPerson(person) }) {
                                PersonCompactRow(person.displayName, person.initials, Modifier.weight(1f), RelativeDate.lastContact(person.lastInteractionAt, now, state.calendar), person.photoData)
                            }
                        }
                    }
                }
            }
            if (digest.recent.isNotEmpty()) sections += {
                GroupedSection(header = "Recent") {
                    digest.recent.forEach { person ->
                        row(compactTextStart) {
                            ListRow(onClick = { onOpenPerson(person) }) {
                                PersonCompactRow(person.displayName, person.initials, Modifier.weight(1f), recentDetail(person, state), person.photoData)
                            }
                        }
                    }
                }
            }

            sections.forEachIndexed { index, section ->
                if (index > 0) Spacer(Modifier.height(26.dp))
                section()
            }
            TabBarClearance()
        }
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 0..11 -> "Good Morning"
    in 12..16 -> "Good Afternoon"
    else -> "Good Evening"
}

private fun recentDetail(person: Person, state: AppState): String {
    val last = person.lastInteractionAt
    if (last == null || last < person.createdAt) {
        return "Added ${RelativeDate.past(person.createdAt, state.now(), state.calendar).lowercase()}"
    }
    return RelativeDate.lastContact(last, state.now(), state.calendar)
}

@Composable
private fun FollowUpSection(
    title: String,
    followUps: List<FollowUp>,
    state: AppState,
    haptics: Haptics,
    onOpenPerson: (Person) -> Unit,
) {
    val colors = KeeprTheme.colors
    GroupedSection(header = title) {
        followUps.forEach { followUp ->
            row(followUpTextStart) {
                SwipeActionsRow(
                    leading = listOf(SwipeAction("Tomorrow", "clock", colors.orange) { state.snooze(followUp, 1, haptics) }),
                    trailing = listOf(SwipeAction("Done", "checkmark", colors.green) { state.toggle(followUp, haptics) }),
                ) {
                    ListRow(onClick = { followUp.person?.let(onOpenPerson) }) {
                        FollowUpRecordRow(state, followUp, haptics, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** "Time to Reach Out": who, when you last spoke, and how overdue the rhythm is. */
private fun GroupedSectionScope.dueRow(item: CadenceItem, state: AppState, haptics: Haptics, onOpenPerson: (Person) -> Unit) {
    row(compactTextStart) {
        val colors = KeeprTheme.colors
        SwipeActionsRow(trailing = listOf(SwipeAction("Reached Out", "paperplane", colors.blue) { state.markReachedOut(item.person, haptics) })) {
            ListRow(onClick = { onOpenPerson(item.person) }) {
                PersonCompactRow(
                    item.person.displayName, item.person.initials, Modifier.weight(1f),
                    RelativeDate.lastContact(item.person.lastInteractionAt, state.now(), state.calendar), item.person.photoData,
                )
                // iOS lays the chevron and the status out as two flexible items,
                // which leaves this gap; kept so the row reads the same.
                Spacer(Modifier.width(20.dp))
                KText(
                    item.status.summary,
                    style = KeeprTheme.typography.caption.medium,
                    color = if (item.status.daysOverdue > 0) colors.orange else colors.secondaryLabel,
                )
            }
        }
    }
}
