package com.aulorvy.keepr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.aulorvy.keepr.core.domain.FollowUpEngine
import com.aulorvy.keepr.core.domain.Outreach
import com.aulorvy.keepr.core.model.FollowUp
import com.aulorvy.keepr.core.model.Interaction
import com.aulorvy.keepr.core.model.InteractionKind
import com.aulorvy.keepr.core.model.Memory
import com.aulorvy.keepr.core.model.MemoryDraft
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.trimmed
import com.aulorvy.keepr.core.model.trimmedSpaces
import com.aulorvy.keepr.core.services.FollowUpScheduler
import com.aulorvy.keepr.ui.components.FormDateRow
import com.aulorvy.keepr.ui.components.FormMenuPicker
import com.aulorvy.keepr.ui.components.FormTextField
import com.aulorvy.keepr.ui.components.FormToggle
import com.aulorvy.keepr.ui.components.GroupedSection
import com.aulorvy.keepr.ui.components.KText
import com.aulorvy.keepr.ui.components.KeeprButton
import com.aulorvy.keepr.ui.components.KeeprSheet
import com.aulorvy.keepr.ui.components.KeeprToggle
import com.aulorvy.keepr.ui.components.ListRow
import com.aulorvy.keepr.ui.components.SectionSpacer
import com.aulorvy.keepr.ui.components.SheetBody
import com.aulorvy.keepr.ui.components.SheetHeader
import com.aulorvy.keepr.ui.components.rememberHaptics
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.semibold

/**
 * Record a meaningful interaction: what kind, when, what happened, facts worth
 * remembering, and an optional follow-up. Translated from
 * Keepr/Features/Person/LogInteractionView.swift.
 */
@Composable
fun LogInteractionSheet(person: Person, onDismiss: () -> Unit) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    var kind by rememberSaveable { mutableStateOf(InteractionKind.IN_PERSON) }
    var occurredAt by remember { mutableStateOf(state.now()) }
    var note by rememberSaveable { mutableStateOf("") }
    var memoryDrafts by remember { mutableStateOf(listOf<MemoryDraft>()) }
    var newMemory by rememberSaveable { mutableStateOf("") }
    var wantsFollowUp by rememberSaveable { mutableStateOf(false) }
    var followUpTitle by rememberSaveable { mutableStateOf("") }
    var followUpDate by remember { mutableStateOf(FollowUpEngine.DuePreset.NEXT_WEEK.date(state.now(), state.calendar)) }
    val noteFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { noteFocus.requestFocus() } }

    val canSave = note.trimmed().isNotEmpty() || memoryDrafts.any { it.isSelected }

    fun addMemory() {
        val content = newMemory.trimmed()
        if (content.isEmpty()) return
        memoryDrafts = memoryDrafts + MemoryDraft(content)
        newMemory = ""
        haptics.light()
    }

    fun save() {
        val trimmedNote = note.trimmed()
        val interaction = Interaction(kind, occurredAt, rawNote = trimmedNote.ifEmpty { null }, person = person)
        state.context.insert(interaction)
        for (draft in memoryDrafts.filter { it.isSelected }) {
            state.context.insert(Memory(draft.content, draft.category, draft.importance, state.now(), person = person, sourceInteraction = interaction))
        }
        val trimmedFollowUp = followUpTitle.trimmed()
        if (wantsFollowUp && trimmedFollowUp.isNotEmpty()) {
            val followUp = FollowUp(
                trimmedFollowUp,
                dueDate = state.calendar.startOfDay(followUpDate),
                person = person,
                sourceInteraction = interaction,
                createdAt = state.now(),
            )
            state.context.insert(followUp)
            FollowUpScheduler.sync(followUp, state.notifications, state.remindersEnabled, state.scope)
        }
        val last = person.lastInteractionAt
        if (last == null || last < occurredAt) person.lastInteractionAt = occurredAt
        Outreach.markReplied(person, occurredAt)
        person.touch(state.now())
        state.save()
        haptics.success()
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        SheetHeader("Log Interaction", onCancel = onDismiss, confirmTitle = "Save", confirmEnabled = canSave, onConfirm = ::save)
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            GroupedSection {
                row {
                    FormMenuPicker("Type", InteractionKind.entries, kind, { it.title }, { kind = it }, symbol = { it.symbolName })
                }
                row { FormDateRow("When", occurredAt, { occurredAt = it }, showsTime = true, latest = state.now(), calendar = state.calendar) }
            }
            SectionSpacer()
            GroupedSection(header = "What happened") {
                row { FormTextField(note, { note = it }, "Talked about…", minLines = 3, maxLines = 8, focusRequester = noteFocus) }
            }
            SectionSpacer()
            GroupedSection(header = "Remember", footer = "Facts saved here show up at the top of ${person.displayName}'s profile.") {
                memoryDrafts.forEachIndexed { index, draft ->
                    row {
                        ListRow {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                KText(draft.content, style = KeeprTheme.typography.subheadline)
                                KText(draft.category.title, style = KeeprTheme.typography.caption2, color = KeeprTheme.colors.secondaryLabel)
                            }
                            KeeprToggle(draft.isSelected, { selected ->
                                memoryDrafts = memoryDrafts.toMutableList().also { it[index] = draft.copy(isSelected = selected) }
                            })
                        }
                    }
                }
                row {
                    ListRow(verticalPadding = 0.dp) {
                        FormTextField(newMemory, { newMemory = it }, "Something to remember", Modifier.weight(1f), style = KeeprTheme.typography.subheadline, onSubmit = ::addMemory)
                        if (newMemory.trimmedSpaces().isNotEmpty()) {
                            KeeprButton("Add", ::addMemory, textStyle = KeeprTheme.typography.subheadline.semibold)
                        }
                    }
                }
            }
            SectionSpacer()
            GroupedSection {
                row { FormToggle("Add a follow-up", wantsFollowUp, { wantsFollowUp = it }) }
                if (wantsFollowUp) {
                    row { FormTextField(followUpTitle, { followUpTitle = it }, "What do you need to do?") }
                    row { FormDateRow("Due", followUpDate, { followUpDate = it }, calendar = state.calendar) }
                }
            }
        }
    }
}
