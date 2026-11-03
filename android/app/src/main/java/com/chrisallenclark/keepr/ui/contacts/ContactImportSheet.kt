package com.chrisallenclark.keepr.ui.contacts

import androidx.compose.runtime.Composable
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.ui.screens.ComingSoonSheet

/** Placeholder until the feature lands. */
@Composable
fun ContactImportSheet(mode: ContextMode, onDismiss: () -> Unit) {
    ComingSoonSheet("Importing from contacts", onDismiss = onDismiss)
}
