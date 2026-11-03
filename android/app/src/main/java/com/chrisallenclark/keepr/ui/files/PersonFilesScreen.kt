package com.chrisallenclark.keepr.ui.files

import androidx.compose.runtime.Composable
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.ui.screens.ComingSoonSheet

/** Placeholder until the feature lands. */
@Composable
fun PersonFilesScreen(person: Person, onBack: () -> Unit) {
    ComingSoonSheet("Files", onDismiss = onBack)
}
