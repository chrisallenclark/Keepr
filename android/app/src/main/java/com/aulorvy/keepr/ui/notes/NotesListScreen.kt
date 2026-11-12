package com.aulorvy.keepr.ui.notes

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aulorvy.keepr.core.domain.NoteEditor
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.ui.components.EmptyState
import com.aulorvy.keepr.ui.components.EmptyStateArea
import com.aulorvy.keepr.ui.components.KeeprButton
import com.aulorvy.keepr.ui.components.KeeprButtonStyle
import com.aulorvy.keepr.ui.components.KeeprTopBar
import com.aulorvy.keepr.ui.components.SearchField
import com.aulorvy.keepr.ui.components.TabBarClearance
import com.aulorvy.keepr.ui.components.ToolbarButton
import com.aulorvy.keepr.ui.navigation.LocalNavigator
import com.aulorvy.keepr.ui.navigation.SheetRoute
import com.aulorvy.keepr.ui.screens.observeStore
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Spacing

/**
 * Every write-up on one person, newest first with pinned ones leading.
 * Translated from `NotesListView` in Keepr/Features/Person/NoteCard.swift.
 */
@Composable
fun NotesListScreen(person: Person, onBack: () -> Unit) {
    val navigator = LocalNavigator.current
    observeStore().value
    val colors = KeeprTheme.colors

    var query by rememberSaveable { mutableStateOf("") }

    if (person.isDeleted) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val notes = NoteEditor.filter(person.noteList, query)
    fun add() = navigator.present(SheetRoute.NoteComposer(person.id))

    Column(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        KeeprTopBar(
            title = "Notes",
            background = colors.groupedBackground,
            leading = { ToolbarButton("chevron.left", "Back", onBack) },
            trailing = { ToolbarButton("plus", "Add Note", ::add) },
            pinned = {
                SearchField(query, { query = it }, "Search these notes")
                Spacer(Modifier.height(Spacing.small))
            },
        )

        if (notes.isEmpty()) {
            EmptyStateArea(Modifier.weight(1f).fillMaxWidth()) {
                if (query.isBlank()) {
                    EmptyState(
                        "No notes yet",
                        "doc.text",
                        description = "Paste a consultation summary or meeting notes and they'll live here.",
                    ) {
                        KeeprButton("Add Note", ::add, style = KeeprButtonStyle.Prominent)
                    }
                } else {
                    EmptyState(
                        "No Results for “$query”",
                        "magnifyingglass",
                        description = "Check the spelling or try a new search.",
                    )
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(Spacing.small))
                notes.forEach { note ->
                    key(note.id) { NoteCard(note, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                }
                TabBarClearance()
            }
        }
    }
}
