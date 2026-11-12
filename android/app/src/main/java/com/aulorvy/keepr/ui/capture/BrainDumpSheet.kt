package com.aulorvy.keepr.ui.capture

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.aulorvy.keepr.core.model.MemoryCategory
import com.aulorvy.keepr.core.model.MemoryDraft
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.Priority
import com.aulorvy.keepr.core.model.trimmed
import com.aulorvy.keepr.core.services.BrainDumpWorkflow
import com.aulorvy.keepr.core.services.HeuristicCaptureExtractor
import com.aulorvy.keepr.ui.components.EmptyState
import com.aulorvy.keepr.ui.components.FormToggle
import com.aulorvy.keepr.ui.components.GroupedSection
import com.aulorvy.keepr.ui.components.IconLabel
import com.aulorvy.keepr.ui.components.KText
import com.aulorvy.keepr.ui.components.KeeprButton
import com.aulorvy.keepr.ui.components.KeeprMenu
import com.aulorvy.keepr.ui.components.KeeprSheet
import com.aulorvy.keepr.ui.components.ListRow
import com.aulorvy.keepr.ui.components.MenuEntry
import com.aulorvy.keepr.ui.components.SectionSpacer
import com.aulorvy.keepr.ui.components.SheetBody
import com.aulorvy.keepr.ui.components.SheetHeader
import com.aulorvy.keepr.ui.components.rememberHaptics
import com.aulorvy.keepr.ui.icons.SfSymbol
import com.aulorvy.keepr.ui.screens.LocalAppState
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Radius
import com.aulorvy.keepr.ui.theme.Spacing
import com.aulorvy.keepr.ui.theme.medium
import com.aulorvy.keepr.ui.theme.semibold
import kotlinx.coroutines.launch

private const val BRAIN_DUMP_PLACEHOLDER =
    "Everything you know about them.\n\nTheir family, what they do, what they're working towards, what they told " +
        "you, anything you'd want in front of you next time you see them."

/**
 * Empty your head about one person, and let the app file it. Translated from
 * Keepr/Features/Person/BrainDumpView.swift.
 *
 * Takes a paragraph and hands back one card per fact, each filed under a
 * category, and asks you to confirm. The confirming is the point: the app
 * never writes to someone's record from a guess.
 *
 * [initialText] is the text to start from when facts are being pulled out of a
 * conversation write-up rather than typed fresh.
 */
@Composable
fun BrainDumpSheet(person: Person, onDismiss: () -> Unit, initialText: String = "") {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val extractor = remember { HeuristicCaptureExtractor() }

    // Seeded once, so coming back from the review step keeps the user's edits.
    var text by rememberSaveable { mutableStateOf(initialText) }
    var drafts by remember { mutableStateOf(listOf<MemoryDraft>()) }
    var hasSorted by remember { mutableStateOf(false) }
    var isSorting by remember { mutableStateOf(false) }
    var alsoLogInteraction by remember { mutableStateOf(false) }
    val writingFocus = remember { FocusRequester() }

    val trimmed = text.trimmed()
    val keptCount = BrainDumpWorkflow.keptCount(drafts)

    // Seeded text means the user is pulling facts out of something they
    // already have, so the keyboard shouldn't jump up over it.
    LaunchedEffect(hasSorted) {
        if (!hasSorted && text.isEmpty()) runCatching { writingFocus.requestFocus() }
    }

    fun sort() {
        if (trimmed.isEmpty() || isSorting) return
        isSorting = true
        val input = trimmed
        scope.launch {
            drafts = extractor.facts(input)
            hasSorted = true
            isSorting = false
            focusManager.clearFocus()
            haptics.success()
        }
    }

    fun toggleAll() {
        drafts = BrainDumpWorkflow.toggleAll(drafts)
        haptics.selection()
    }

    fun update(index: Int, transform: (MemoryDraft) -> MemoryDraft) {
        drafts = drafts.toMutableList().also { it[index] = transform(it[index]) }
    }

    fun save() {
        BrainDumpWorkflow.save(person, drafts, alsoLogInteraction, state.context, state.now())
        state.save()
        haptics.success()
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        // The sheet is its own window, so back has to be handled inside it
        // (outside, the sheet would close instead). Composed only while there
        // is a step to go back to, so its callback is registered after the
        // sheet's own dismiss callback and always takes priority.
        if (hasSorted) BackHandler { hasSorted = false }
        if (!hasSorted) {
            SheetHeader(
                "Brain Dump",
                onCancel = onDismiss,
                confirmTitle = "Sort It Out",
                confirmEnabled = trimmed.isNotEmpty() && !isSorting,
                onConfirm = ::sort,
            )
            // Scrolls, so a long dump (or a small phone with the keyboard up)
            // never pushes the text or the footnote out of reach.
            SheetBody(Modifier.verticalScroll(rememberScrollState())) {
                WritingPad(
                    text,
                    { text = it },
                    BRAIN_DUMP_PLACEHOLDER,
                    accessibilityLabel = "Everything you know about them.",
                    minHeight = 300.dp,
                    focusRequester = writingFocus,
                )
                KText(
                    "Write it however it comes out — or hold the mic on your keyboard and talk. Keepr splits it into separate facts and files them; you decide what's kept.",
                    style = typography.caption,
                    color = colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            return@KeeprSheet
        }

        SheetHeader(
            "Keep What Matters",
            cancelTitle = "Back",
            onCancel = { hasSorted = false },
            confirmTitle = "Save $keptCount",
            confirmEnabled = keptCount > 0,
            onConfirm = ::save,
        )
        if (drafts.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                EmptyState(
                    "Nothing to file",
                    "text.badge.xmark",
                    description = "Keepr couldn't find separate facts in that. Go back and try a line per thing.",
                )
            }
            return@KeeprSheet
        }
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            GroupedSection(
                header = "${drafts.size} found",
                footer = "Tap a category to change it, or the text to edit it. Anything switched off is thrown away.",
                headerTrailing = {
                    KeeprButton(
                        if (keptCount == drafts.size) "Keep None" else "Keep All",
                        ::toggleAll,
                        textStyle = typography.caption.semibold,
                    )
                },
            ) {
                drafts.forEachIndexed { index, draft ->
                    row(separatorStart = 16.dp + 24.dp + Spacing.medium) {
                        DraftRow(
                            draft,
                            onToggle = {
                                update(index) { it.copy(isSelected = !it.isSelected) }
                                haptics.selection()
                            },
                            onContentChange = { content -> update(index) { it.copy(content = content) } },
                            onCategoryChange = { category ->
                                update(index) { it.copy(category = category) }
                                haptics.selection()
                            },
                            onImportanceToggle = {
                                update(index) { it.copy(importance = if (it.importance == Priority.HIGH) Priority.NORMAL else Priority.HIGH) }
                                haptics.selection()
                            },
                        )
                    }
                }
            }
            SectionSpacer()
            GroupedSection(footer = "Records that you spoke today and restarts how often Keepr expects you to reach out to them.") {
                row { FormToggle("Also log this as an interaction", alsoLogInteraction, { alsoLogInteraction = it }) }
            }
        }
    }
}

/** One found fact: kept or not, its words, its shelf, and whether it matters most. */
@Composable
private fun DraftRow(
    draft: MemoryDraft,
    onToggle: () -> Unit,
    onContentChange: (String) -> Unit,
    onCategoryChange: (MemoryCategory) -> Unit,
    onImportanceToggle: () -> Unit,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    var choosingCategory by remember { mutableStateOf(false) }

    ListRow(verticalPadding = 12.dp, verticalAlignment = Alignment.Top) {
        SfSymbol(
            if (draft.isSelected) "checkmark.circle.fill" else "circle",
            if (draft.isSelected) colors.accent else colors.secondaryLabel,
            typography.title3,
            contentDescription = if (draft.isSelected) "Don't keep" else "Keep",
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Checkbox,
                    onClick = onToggle,
                ),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            BasicTextField(
                value = draft.content,
                onValueChange = onContentChange,
                textStyle = typography.subheadline.copy(color = if (draft.isSelected) colors.label else colors.secondaryLabel),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Fact" },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Box {
                    IconLabel(
                        draft.category.title,
                        draft.category.symbolName,
                        typography.caption2.medium,
                        colors.label,
                        modifier = Modifier
                            .background(colors.tertiaryFill, RoundedCornerShape(Radius.chip))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) { choosingCategory = true }
                            .padding(horizontal = Spacing.small, vertical = 3.dp),
                        maxLines = 1,
                    )
                    KeeprMenu(
                        expanded = choosingCategory,
                        onDismiss = { choosingCategory = false },
                        entries = MemoryCategory.entries.map { category ->
                            MenuEntry.Action(category.title, category.symbolName, isChecked = category == draft.category) {
                                onCategoryChange(category)
                            }
                        },
                    )
                }
                SfSymbol(
                    if (draft.importance == Priority.HIGH) "star.fill" else "star",
                    if (draft.importance == Priority.HIGH) colors.yellow else colors.secondaryLabel,
                    typography.caption,
                    contentDescription = "Mark as important",
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onImportanceToggle,
                    ),
                )
            }
        }
    }
}
