package com.chrisallenclark.keepr.ui.notes

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.NoteEditor
import com.chrisallenclark.keepr.core.model.ConversationNote
import com.chrisallenclark.keepr.core.model.PersonFile
import com.chrisallenclark.keepr.ui.components.DisclosureChevron
import com.chrisallenclark.keepr.ui.components.FloatingCard
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprMenu
import com.chrisallenclark.keepr.ui.components.MenuEntry
import com.chrisallenclark.keepr.ui.components.Separator
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.files.FileViewer
import com.chrisallenclark.keepr.ui.files.PersonFileThumbnail
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.navigation.LocalNavigator
import com.chrisallenclark.keepr.ui.navigation.SheetRoute
import com.chrisallenclark.keepr.ui.screens.ConfirmDialog
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.medium
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * A write-up as a card that opens where it sits. Translated from
 * Keepr/Features/Person/NoteCard.swift.
 *
 * Closed, it's a title, a date and two lines — enough to recognise without
 * reading. Open, it's the whole thing. Expanding in place rather than pushing
 * a new screen is deliberate: these get skimmed in sequence, and a push loses
 * your place every time.
 *
 * [showsActions] hides the action row on the profile, where space is tight;
 * a long press offers the same actions everywhere.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: ConversationNote,
    modifier: Modifier = Modifier,
    showsActions: Boolean = true,
) {
    val state = LocalAppState.current
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography

    var isExpanded by rememberSaveable(note.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PersonFile?>(null) }

    val person = note.person

    fun pullFacts() {
        person?.let { navigator.present(SheetRoute.BrainDump(it.id, initialText = note.body)) }
    }

    fun edit() {
        person?.let { navigator.present(SheetRoute.NoteComposer(it.id, note.id)) }
    }

    fun togglePin() {
        NoteEditor.togglePin(note, state.now())
        state.save()
        haptics.selection()
    }

    fun delete() {
        val owner = note.person
        state.context.delete(note)
        owner?.touch(state.now())
        state.save()
    }

    val menu = buildList<MenuEntry> {
        if (note.hasBody) add(MenuEntry.Action("Pull Facts", "brain", onClick = ::pullFacts))
        add(MenuEntry.Action(if (note.isPinned) "Unpin" else "Pin", if (note.isPinned) "pin.slash" else "pin", onClick = ::togglePin))
        add(MenuEntry.Action("Edit", "pencil", onClick = ::edit))
        add(MenuEntry.Divider)
        add(MenuEntry.Action("Delete", "trash", isDestructive = true) { confirmingDelete = true })
    }

    Box(modifier) {
        FloatingCard(
            Modifier
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        isExpanded = !isExpanded
                        haptics.light()
                    },
                    onLongClick = {
                        haptics.light()
                        menuOpen = true
                    },
                )
                .semantics {
                    stateDescription = if (isExpanded) "Expanded" else "Collapsed"
                },
            isHighlighted = note.isPinned,
        ) {
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                // Header
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    SfSymbol(
                        if (note.isPinned) "pin.fill" else note.source.symbolName,
                        if (note.isPinned) colors.accent else colors.secondaryLabel,
                        typography.caption,
                        Modifier.width(16.dp).padding(top = 2.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        KText(note.title, style = typography.subheadline.semibold)
                        KText(
                            "${state.calendar.abbreviatedDate(note.occurredAt)} · ${note.lengthLabel}",
                            style = typography.caption2,
                            color = colors.tertiaryLabel,
                        )
                    }
                    Spacer(Modifier.width(Spacing.tight))
                    DisclosureChevron(isExpanded, Modifier.padding(top = 2.dp))
                }

                if (isExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                        Separator(start = 0.dp, end = 0.dp)
                        if (note.hasBody) {
                            // Selectable so a line can be copied straight back out —
                            // these came from somewhere else and often need to go somewhere else.
                            SelectionContainer {
                                KText(note.body, style = typography.subheadline)
                            }
                        }
                        val images = note.fileList
                        if (images.isNotEmpty()) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                            ) {
                                images.forEach { file ->
                                    PersonFileThumbnail(
                                        file,
                                        size = 72.dp,
                                        modifier = Modifier
                                            .clickable(remember { MutableInteractionSource() }, null) { viewing = file }
                                            .semantics { contentDescription = file.title },
                                    )
                                }
                            }
                        }
                        if (showsActions) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                            ) {
                                if (note.hasBody) CardButton("Pull Facts", "brain", ::pullFacts)
                                CardButton(if (note.isPinned) "Unpin" else "Pin", if (note.isPinned) "pin.slash" else "pin", ::togglePin)
                                CardButton("Edit", "pencil", ::edit)
                                Spacer(Modifier.weight(1f))
                                SfSymbol(
                                    "trash",
                                    colors.secondaryLabel,
                                    typography.caption,
                                    contentDescription = "Delete note",
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                    ) { confirmingDelete = true },
                                )
                            }
                        }
                    }
                }
            }
        }
        KeeprMenu(menuOpen, { menuOpen = false }, menu)
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete this note?",
            message = "The write-up and its screenshots are removed. Anything you've already pulled into What You Know stays.",
            confirmTitle = "Delete",
            onConfirm = ::delete,
            onDismiss = { confirmingDelete = false },
        )
    }
    viewing?.let { file -> FileViewer(file, onDismiss = { viewing = null }) }
}

@Composable
private fun CardButton(title: String, symbolName: String, onClick: () -> Unit) {
    val colors = KeeprTheme.colors
    IconLabel(
        title,
        symbolName,
        KeeprTheme.typography.caption.medium,
        colors.accent,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        ),
        maxLines = 1,
    )
}
