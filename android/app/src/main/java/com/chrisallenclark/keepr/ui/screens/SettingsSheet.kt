package com.chrisallenclark.keepr.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.BuildConfig
import com.chrisallenclark.keepr.core.persistence.KeeprStore
import com.chrisallenclark.keepr.core.persistence.SampleData
import com.chrisallenclark.keepr.ui.components.FormToggle
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.LabeledRow
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.bold

/**
 * The part of iOS Settings this phase needs: sample data on and off, and
 * erasing everything. Permissions, reminders and the "what Keepr can't do"
 * page arrive with the features they describe.
 */
@Composable
fun SettingsSheet(onDismiss: () -> Unit, onOpenGallery: (() -> Unit)?) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    observeStore().value
    var confirmingDelete by remember { mutableStateOf(false) }
    val sampleLoaded = SampleData.isLoaded(state.context)

    KeeprSheet(onDismiss = onDismiss) {
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            SheetHeader("Settings", cancelTitle = "Done", onCancel = onDismiss)
            Spacer(Modifier.height(8.dp))
            GroupedSection(
                header = "Development",
                footer = "Sample relationships are marked as samples and removed on their own, without touching anyone you added.",
            ) {
                row {
                    FormToggle("Sample data", sampleLoaded, { on ->
                        if (on) SampleData.load(state.context, state.repository.preferences, state.now(), state.calendar)
                        else SampleData.remove(state.context)
                        haptics.success()
                    })
                }
                if (onOpenGallery != null) {
                    row { ListRow(onClick = onOpenGallery) { KText("Design Gallery", color = KeeprTheme.colors.accent) } }
                }
            }
            SectionSpacer()
            GroupedSection(footer = "Removes every person, note and follow-up Keepr holds. Your phone's contacts are not touched.") {
                row { ListRow(onClick = { confirmingDelete = true }) { KText("Delete All Data", color = KeeprTheme.colors.red) } }
            }
            SectionSpacer()
            GroupedSection(header = "About") {
                row { LabeledRow("Version", BuildConfig.VERSION_NAME) }
                row { LabeledRow("Storage", "On this phone only") }
            }
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete all data?",
            message = "This can't be undone. Contacts on your phone are not affected.",
            confirmTitle = "Delete All Data",
            onConfirm = {
                KeeprStore.deleteAllData(state.context, state.repository.preferences)
                haptics.success()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/** Shown where iOS opens a screen this Android build doesn't have yet. */
@Composable
fun ComingSoonSheet(feature: String, onDismiss: () -> Unit) {
    val colors = KeeprTheme.colors
    KeeprSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SfSymbol("sparkles", colors.accent, 44.dp)
            KText("$feature is on its way", style = KeeprTheme.typography.title3.bold, textAlign = TextAlign.Center)
            KText(
                "It's part of the next Android update. Everything you record now will be there when it arrives.",
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
