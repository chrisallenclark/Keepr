package com.chrisallenclark.keepr.ui.notes

import androidx.compose.runtime.Composable
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.ui.screens.ComingSoonSheet

/** Placeholder until the feature lands. */
@Composable
fun NoteComposerSheet(person: Person, note: com.chrisallenclark.keepr.core.model.ConversationNote?, onDismiss: () -> Unit) {
    ComingSoonSheet("Notes", onDismiss = onDismiss)
}
