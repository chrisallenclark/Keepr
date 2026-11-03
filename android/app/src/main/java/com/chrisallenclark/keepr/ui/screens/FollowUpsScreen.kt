package com.chrisallenclark.keepr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.FollowUpBucket
import com.chrisallenclark.keepr.core.domain.FollowUpEngine
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.ui.components.ContextSwitcher
import com.chrisallenclark.keepr.ui.components.EmptyState
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.KeeprMenu
import com.chrisallenclark.keepr.ui.components.KeeprTopBar
import com.chrisallenclark.keepr.ui.components.ListMetrics
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.MenuEntry
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SwipeAction
import com.chrisallenclark.keepr.ui.components.SwipeActionsRow
import com.chrisallenclark.keepr.ui.components.TabBarClearance
import com.chrisallenclark.keepr.ui.components.ToolbarButton
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing

/** Where a follow-up row's text starts: past the 24pt circle and a 12pt gap. */
internal val followUpTextStart = ListMetrics.rowHorizontal + 24.dp + Spacing.medium

/**
 * The Follow Up tab: every open follow-up in the current context, by when it's
 * due. Translated from Keepr/Features/FollowUps/FollowUpsView.swift.
 */
@Composable
fun FollowUpsScreen(mode: ModeBinding, onOpenPerson: (Person) -> Unit) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    observeStore().value
    var showsCompleted by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FollowUp?>(null) }
    var menuFor by remember { mutableStateOf<FollowUp?>(null) }

    val all = state.context.fetch<FollowUp>().sortedBy { it.dueDate }
    val sections = FollowUpEngine.grouped(FollowUpEngine.visible(all, mode.mode), state.now(), state.calendar)
        .filter { showsCompleted || it.bucket != FollowUpBucket.COMPLETED }

    Column(Modifier.fillMaxSize().background(KeeprTheme.colors.groupedBackground)) {
        KeeprTopBar(
            title = "Follow Up",
            trailing = {
                Box {
                    ToolbarButton("ellipsis.circle", "Options", { optionsOpen = true })
                    KeeprMenu(
                        expanded = optionsOpen,
                        onDismiss = { optionsOpen = false },
                        entries = listOf(MenuEntry.Action("Show Completed", isChecked = showsCompleted) { showsCompleted = !showsCompleted }),
                    )
                }
            },
            pinned = { ContextSwitcher(mode.mode == ContextMode.PERSONAL, { mode.set(if (it) ContextMode.PERSONAL else ContextMode.BUSINESS) }) },
        )
        if (sections.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Nothing to follow up on",
                    symbolName = "bell.slash",
                    description = "Add a follow-up from anyone's profile and it'll show up here.",
                    modifier = Modifier.padding(bottom = 60.dp),
                )
            }
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(10.dp))
            sections.forEachIndexed { index, section ->
                if (index > 0) SectionSpacer()
                GroupedSection(header = section.title) {
                    section.items.forEach { followUp ->
                        row(followUpTextStart) {
                            val leading = if (followUp.isCompleted) emptyList() else listOf(
                                SwipeAction("Tomorrow", "clock", KeeprTheme.colors.orange) { state.snooze(followUp, 1, haptics) },
                                SwipeAction("Next Week", "calendar", KeeprTheme.colors.blue) { state.snooze(followUp, 7, haptics) },
                            )
                            val trailing = listOf(
                                if (followUp.isCompleted) {
                                    SwipeAction("Reopen", "arrow.uturn.backward", KeeprTheme.colors.gray) { state.toggle(followUp, haptics) }
                                } else {
                                    SwipeAction("Done", "checkmark", KeeprTheme.colors.green) { state.toggle(followUp, haptics) }
                                },
                                SwipeAction("Delete", "trash", KeeprTheme.colors.red) { state.delete(followUp) },
                            )
                            Box {
                                SwipeActionsRow(leading = leading, trailing = trailing) {
                                    ListRow(
                                        onClick = { followUp.person?.let(onOpenPerson) },
                                        onLongClick = { menuFor = followUp },
                                    ) {
                                        FollowUpRecordRow(state, followUp, haptics, Modifier.weight(1f))
                                    }
                                }
                                KeeprMenu(
                                    expanded = menuFor === followUp,
                                    onDismiss = { menuFor = null },
                                    entries = listOfNotNull(
                                        MenuEntry.Action("Edit", "pencil") { editing = followUp },
                                        followUp.person?.let { person ->
                                            MenuEntry.Action("Open ${person.displayName}", "person") { onOpenPerson(person) }
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            TabBarClearance()
        }
    }

    editing?.let { followUp ->
        val person = followUp.person
        if (person != null) FollowUpEditorSheet(person, followUp, onDismiss = { editing = null })
    }
}
