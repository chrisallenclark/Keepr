package com.chrisallenclark.keepr.ui.capture

import androidx.compose.runtime.Composable
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.ui.screens.ComingSoonSheet

/** Placeholder until the feature lands. */
@Composable
fun QuickCaptureSheet(mode: ContextMode, onDismiss: () -> Unit) {
    ComingSoonSheet("Quick Capture", onDismiss = onDismiss)
}
