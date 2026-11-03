package com.chrisallenclark.keepr.ui.capture

import androidx.compose.runtime.Composable
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.ui.screens.ComingSoonSheet

/** Placeholder until the feature lands. */
@Composable
fun BrainDumpSheet(person: Person, onDismiss: () -> Unit) {
    ComingSoonSheet("Brain Dump", onDismiss = onDismiss)
}
