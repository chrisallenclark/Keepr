package com.chrisallenclark.keepr.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.FollowUpEngine
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.Priority
import com.chrisallenclark.keepr.core.model.trimmed
import com.chrisallenclark.keepr.core.model.trimmedSpaces
import com.chrisallenclark.keepr.core.services.FollowUpScheduler
import com.chrisallenclark.keepr.ui.components.FormDateRow
import com.chrisallenclark.keepr.ui.components.FormTextField
import com.chrisallenclark.keepr.ui.components.FormToggle
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.PresetChips
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SegmentedControl
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.theme.KeeprTheme

/**
 * New or edit follow-up. Translated from
 * Keepr/Features/FollowUps/FollowUpEditor.swift.
 */
@Composable
fun FollowUpEditorSheet(person: Person, followUp: FollowUp?, onDismiss: () -> Unit) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    var title by rememberSaveable { mutableStateOf(followUp?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(followUp?.note ?: "") }
    var dueDate by remember { mutableStateOf(followUp?.dueDate ?: FollowUpEngine.DuePreset.NEXT_WEEK.date(state.now(), state.calendar)) }
    var hasTime by rememberSaveable { mutableStateOf(followUp?.hasTime ?: false) }
    var priority by rememberSaveable { mutableStateOf(followUp?.priority ?: Priority.NORMAL) }
    val titleFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (followUp == null) runCatching { titleFocus.requestFocus() } }

    fun save() {
        val trimmedTitle = title.trimmed()
        val trimmedNote = note.trimmed()
        val resolved = if (hasTime) dueDate else state.calendar.startOfDay(dueDate)
        val target = if (followUp != null) {
            followUp.title = trimmedTitle
            followUp.note = trimmedNote.ifEmpty { null }
            followUp.dueDate = resolved
            followUp.hasTime = hasTime
            followUp.priority = priority
            followUp
        } else {
            FollowUp(trimmedTitle, trimmedNote.ifEmpty { null }, resolved, hasTime, priority, person = person, createdAt = state.now())
                .also { state.context.insert(it) }
        }
        person.touch(state.now())
        state.save()
        FollowUpScheduler.sync(target, state.notifications, state.remindersEnabled, state.scope)
        haptics.success()
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        SheetHeader(
            if (followUp == null) "New Follow-Up" else "Edit Follow-Up",
            onCancel = onDismiss,
            confirmTitle = "Save",
            confirmEnabled = title.trimmedSpaces().isNotEmpty(),
            onConfirm = ::save,
        )
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            GroupedSection {
                row { FormTextField(title, { title = it }, "What do you need to do?", maxLines = 3, focusRequester = titleFocus) }
                row { FormTextField(note, { note = it }, "Note (optional)", style = KeeprTheme.typography.subheadline, maxLines = 4) }
            }
            SectionSpacer()
            GroupedSection(header = "When") {
                if (followUp == null) {
                    row {
                        PresetChips(
                            FollowUpEngine.DuePreset.entries.map { preset ->
                                preset.title to state.calendar.isSameDay(dueDate, preset.date(state.now(), state.calendar))
                            },
                            onSelect = { index ->
                                dueDate = FollowUpEngine.DuePreset.entries[index].date(state.now(), state.calendar)
                                hasTime = false
                                haptics.selection()
                            },
                        )
                    }
                }
                row { FormDateRow("Due", dueDate, { dueDate = it }, showsTime = hasTime, calendar = state.calendar) }
                row { FormToggle("Set a time", hasTime, { hasTime = it }) }
            }
            SectionSpacer()
            GroupedSection(
                footer = if (state.remindersEnabled) "You'll get one reminder — nothing repeats."
                else "Reminders are off, so this won't notify you. Turn them on in Settings.",
            ) {
                row {
                    ListRow {
                        SegmentedControl(
                            Priority.entries.map { it.title },
                            Priority.entries.indexOf(priority),
                            { priority = Priority.entries[it] },
                        )
                    }
                }
            }
        }
    }
}
