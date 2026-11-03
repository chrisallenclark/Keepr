package com.chrisallenclark.keepr.ui.organize

import androidx.compose.runtime.Composable
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.ui.screens.ComingSoonSheet

/** Placeholder until the feature lands. */
@Composable
fun GroupEditorSheet(group: com.chrisallenclark.keepr.core.model.PersonGroup?, addingTo: Person?, onDismiss: () -> Unit) {
    ComingSoonSheet("Groups", onDismiss = onDismiss)
}
