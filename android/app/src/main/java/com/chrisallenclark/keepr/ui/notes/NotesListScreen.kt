package com.chrisallenclark.keepr.ui.notes

import androidx.compose.runtime.Composable
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.ui.screens.ComingSoonSheet

/** Placeholder until the feature lands. */
@Composable
fun NotesListScreen(person: Person, onBack: () -> Unit) {
    ComingSoonSheet("Notes", onDismiss = onBack)
}
