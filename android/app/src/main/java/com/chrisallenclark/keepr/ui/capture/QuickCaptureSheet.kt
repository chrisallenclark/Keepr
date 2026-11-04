package com.chrisallenclark.keepr.ui.capture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.trimmed
import com.chrisallenclark.keepr.core.services.CaptureDraft
import com.chrisallenclark.keepr.core.services.CaptureSubject
import com.chrisallenclark.keepr.core.services.FollowUpScheduler
import com.chrisallenclark.keepr.core.services.HeuristicCaptureExtractor
import com.chrisallenclark.keepr.core.services.QuickCaptureWorkflow
import com.chrisallenclark.keepr.ui.components.Avatar
import com.chrisallenclark.keepr.ui.components.AvatarSize
import com.chrisallenclark.keepr.ui.components.FormDateRow
import com.chrisallenclark.keepr.ui.components.FormMenuPicker
import com.chrisallenclark.keepr.ui.components.FormTextField
import com.chrisallenclark.keepr.ui.components.FormToggle
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.KeeprToggle
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.semibold
import kotlinx.coroutines.launch

private enum class CaptureStep { CAPTURE, REVIEW }

private const val CAPTURE_PLACEHOLDER =
    "Talked to Jake at the gym. He owns a roofing company, wants to lose 20 lb, and said to text him next week."

/**
 * Get it out of your head in seconds; sort it out second. Translated from
 * Keepr/Features/Capture/QuickCaptureView.swift.
 *
 * Step one is a single text field and nothing else. Step two shows what the
 * on-device extractor made of it — every suggestion is a toggle, and nothing
 * is saved until you confirm.
 */
@Composable
fun QuickCaptureSheet(mode: ContextMode, onDismiss: () -> Unit) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    val scope = rememberCoroutineScope()
    val extractor = remember { HeuristicCaptureExtractor() }

    var step by remember { mutableStateOf(CaptureStep.CAPTURE) }
    var text by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf(CaptureDraft(occurredAt = state.now())) }
    var subject by remember { mutableStateOf(CaptureSubject()) }
    var isPickingPerson by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    val editorFocus = remember { FocusRequester() }

    val trimmedText = text.trimmed()

    LaunchedEffect(step) {
        if (step == CaptureStep.CAPTURE) runCatching { editorFocus.requestFocus() }
    }

    fun runExtraction() {
        if (trimmedText.isEmpty() || isExtracting) return
        isExtracting = true
        val input = trimmedText
        scope.launch {
            val result = extractor.extract(input, state.now())
            draft = result
            // Preselects an existing person when the note names one; otherwise
            // offers to create them. Never creates anything on its own.
            val match = QuickCaptureWorkflow.matchSuggestedPerson(result.suggestedName, state.context.fetch<Person>(), mode)
            val matched = match.person
            if (matched != null) {
                subject = subject.copy(person = matched)
            } else if (match.newPersonName.isNotEmpty()) {
                subject = subject.copy(newPersonName = match.newPersonName)
            }
            isExtracting = false
            step = CaptureStep.REVIEW
        }
    }

    fun save() {
        val result = QuickCaptureWorkflow.save(draft, text, subject, mode, state.context, state.now(), state.calendar) ?: return
        state.save()
        result.followUp?.let { FollowUpScheduler.sync(it, state.notifications, state.remindersEnabled, state.scope) }
        haptics.success()
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        // The sheet is its own window, so back has to be handled inside it
        // (outside, the sheet would close instead).
        BackHandler(enabled = step == CaptureStep.REVIEW) { step = CaptureStep.CAPTURE }
        when (step) {
            CaptureStep.CAPTURE -> {
                SheetHeader(
                    "Quick Capture",
                    onCancel = onDismiss,
                    confirmTitle = "Next",
                    confirmEnabled = trimmedText.isNotEmpty() && !isExtracting,
                    onConfirm = ::runExtraction,
                )
                SheetBody {
                    WritingPad(
                        text,
                        { text = it },
                        CAPTURE_PLACEHOLDER,
                        accessibilityLabel = "What happened?",
                        minHeight = 280.dp,
                        focusRequester = editorFocus,
                    )
                    KText(
                        "Stays on your device.",
                        style = typography.footnote,
                        color = colors.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            CaptureStep.REVIEW -> {
                SheetHeader(
                    "Review",
                    cancelTitle = "Back",
                    onCancel = { step = CaptureStep.CAPTURE },
                    confirmTitle = "Save",
                    confirmEnabled = subject.isChosen,
                    onConfirm = ::save,
                )
                SheetBody(Modifier.verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(8.dp))
                    GroupedSection(header = "Who") {
                        row {
                            ListRow(onClick = { isPickingPerson = true }) {
                                val chosen = subject.person
                                when {
                                    chosen != null -> {
                                        Avatar(chosen.initials, size = AvatarSize.Small, imageData = chosen.photoData)
                                        KText(chosen.displayName, Modifier.weight(1f), maxLines = 1)
                                    }
                                    subject.newPersonName.isNotEmpty() -> {
                                        SfSymbol("person.crop.circle.badge.plus", colors.secondaryLabel, typography.title3)
                                        KText("New: ${subject.newPersonName}", Modifier.weight(1f), maxLines = 1)
                                    }
                                    else -> {
                                        SfSymbol("person.crop.circle.badge.questionmark", colors.secondaryLabel, typography.title3)
                                        KText("Choose someone", Modifier.weight(1f), color = colors.secondaryLabel)
                                    }
                                }
                                SfSymbol("chevron.right", colors.tertiaryLabel, typography.caption.semibold)
                            }
                        }
                    }
                    SectionSpacer()
                    GroupedSection(header = "Interaction") {
                        row {
                            FormMenuPicker(
                                "Type",
                                InteractionKind.entries,
                                draft.kind,
                                { it.title },
                                { draft = draft.copy(kind = it) },
                                symbol = { it.symbolName },
                            )
                        }
                        row {
                            FormDateRow(
                                "When",
                                draft.occurredAt,
                                { draft = draft.copy(occurredAt = it) },
                                showsTime = true,
                                latest = state.now(),
                                calendar = state.calendar,
                            )
                        }
                    }
                    if (draft.memories.isNotEmpty()) {
                        SectionSpacer()
                        GroupedSection(header = "Remember", footer = "Turn off anything that isn't worth keeping.") {
                            draft.memories.forEachIndexed { index, memory ->
                                row {
                                    ListRow {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            KText(memory.content, style = typography.subheadline)
                                            KText(memory.category.title, style = typography.caption2, color = colors.secondaryLabel)
                                        }
                                        KeeprToggle(memory.isSelected, { selected ->
                                            draft = draft.copy(
                                                memories = draft.memories.toMutableList().also { it[index] = memory.copy(isSelected = selected) },
                                            )
                                        })
                                    }
                                }
                            }
                        }
                    }
                    val followUp = draft.followUp
                    if (followUp != null) {
                        SectionSpacer()
                        GroupedSection(header = "Follow Up") {
                            row {
                                FormToggle("Add this follow-up", followUp.isSelected, { draft = draft.copy(followUp = followUp.copy(isSelected = it)) })
                            }
                            if (followUp.isSelected) {
                                row {
                                    FormTextField(followUp.title, { draft = draft.copy(followUp = followUp.copy(title = it)) }, "What to do")
                                }
                                row {
                                    FormDateRow(
                                        "Due",
                                        followUp.dueDate,
                                        { draft = draft.copy(followUp = followUp.copy(dueDate = it)) },
                                        calendar = state.calendar,
                                    )
                                }
                            }
                        }
                    }
                    SectionSpacer()
                    GroupedSection(header = "Note") {
                        row {
                            ListRow {
                                KText(trimmedText, Modifier.weight(1f), style = typography.subheadline, color = colors.secondaryLabel)
                            }
                        }
                    }
                }
            }
        }
    }

    if (isPickingPerson) {
        PersonPickerSheet(
            suggestedName = draft.suggestedName,
            onPick = { person ->
                subject = CaptureSubject(person = person)
                isPickingPerson = false
            },
            onAddNew = { name ->
                subject = CaptureSubject(newPersonName = name)
                isPickingPerson = false
            },
            onDismiss = { isPickingPerson = false },
        )
    }
}
