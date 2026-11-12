package com.aulorvy.keepr.ui.files

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aulorvy.keepr.core.domain.PersonFileLibrary
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonFile
import com.aulorvy.keepr.ui.components.DisclosureChevron
import com.aulorvy.keepr.ui.components.FloatingCard
import com.aulorvy.keepr.ui.components.GroupedSection
import com.aulorvy.keepr.ui.components.IconLabel
import com.aulorvy.keepr.ui.components.KText
import com.aulorvy.keepr.ui.components.ListMetrics
import com.aulorvy.keepr.ui.components.ListRow
import com.aulorvy.keepr.ui.components.SectionHeader
import com.aulorvy.keepr.ui.components.Separator
import com.aulorvy.keepr.ui.components.rememberHaptics
import com.aulorvy.keepr.ui.icons.SfSymbol
import com.aulorvy.keepr.ui.navigation.LocalNavigator
import com.aulorvy.keepr.ui.navigation.Route
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Spacing
import com.aulorvy.keepr.ui.theme.medium
import com.aulorvy.keepr.ui.theme.semibold

/**
 * The profile's Files section (`filesSection` in PersonProfileView.swift):
 * one card that opens to show what's in it.
 *
 * Closed it answers "is anything on file"; open it shows the thumbnails and
 * the latest name, which is the actual question with a stack of check-in
 * scans. Managing them is a screen of its own.
 */
@Composable
fun ProfileFilesSection(person: Person) {
    val navigator = LocalNavigator.current
    val haptics = rememberHaptics()
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    val files = person.fileList
    val openFiles = { navigator.push(Route.Files(person.id)) }

    var isExpanded by rememberSaveable(person.id) { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<PersonFile?>(null) }

    if (files.isEmpty()) {
        GroupedSection(header = "Files") {
            row {
                ListRow(onClick = openFiles) {
                    IconLabel("Add a photo or file", "paperclip", typography.subheadline, colors.accent)
                }
            }
        }
        return
    }

    Column {
        SectionHeader(
            "Files",
            Modifier.padding(
                start = ListMetrics.sectionInset + ListMetrics.textInset,
                end = ListMetrics.sectionInset + ListMetrics.textInset,
                bottom = 9.dp,
            ),
        )
        FloatingCard(
            Modifier
                .padding(horizontal = ListMetrics.sectionInset)
                .clickable(remember { MutableInteractionSource() }, null) {
                    isExpanded = !isExpanded
                    haptics.light()
                },
        ) {
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                    SfSymbol("paperclip", colors.secondaryLabel, typography.caption, Modifier.width(16.dp).padding(top = 2.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        KText(PersonFileLibrary.countLabel(files.size), style = typography.subheadline.semibold)
                        KText("Latest: ${files.first().title}", style = typography.caption2, color = colors.tertiaryLabel, maxLines = 1)
                    }
                    Spacer(Modifier.width(Spacing.tight))
                    DisclosureChevron(isExpanded, Modifier.padding(top = 2.dp))
                }
                if (isExpanded) {
                    Separator(start = 0.dp, end = 0.dp)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    ) {
                        files.take(8).forEach { file ->
                            PersonFileThumbnail(
                                file,
                                size = 72.dp,
                                modifier = Modifier
                                    .clickable(remember { MutableInteractionSource() }, null) { if (file.hasData) viewing = file }
                                    .semantics { contentDescription = file.title },
                            )
                        }
                    }
                    IconLabel(
                        "Open Files",
                        "folder",
                        typography.caption.medium,
                        colors.accent,
                        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClick = openFiles),
                    )
                }
            }
        }
    }

    viewing?.let { file -> FileViewer(file, onDismiss = { viewing = null }) }
}
