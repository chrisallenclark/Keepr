package com.chrisallenclark.keepr.ui.notes

import androidx.compose.runtime.Composable
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.ui.screens.ComingSoonSheet

/** Placeholder until the feature lands. */
@Composable
fun MemoryLibraryScreen(person: Person, onBack: () -> Unit) {
    ComingSoonSheet("The full memory library", onDismiss = onBack)
}
