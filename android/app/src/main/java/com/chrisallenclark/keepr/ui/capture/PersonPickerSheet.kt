package com.chrisallenclark.keepr.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.services.QuickCaptureWorkflow
import com.chrisallenclark.keepr.ui.components.EmptyState
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.PersonCompactRow
import com.chrisallenclark.keepr.ui.components.SearchField
import com.chrisallenclark.keepr.ui.components.Separator
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.screens.compactTextStart
import com.chrisallenclark.keepr.ui.screens.observeStore
import com.chrisallenclark.keepr.ui.theme.KeeprTheme

/**
 * Choose who a capture is about — an existing relationship, or a new one.
 * Translated from Keepr/Features/Capture/PersonPicker.swift.
 *
 * Lists the whole address book, not just the current mode, so a capture about
 * a friend can still be filed while in Business.
 */
@Composable
fun PersonPickerSheet(
    suggestedName: String?,
    onPick: (Person) -> Unit,
    onAddNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    observeStore().value
    var query by rememberSaveable { mutableStateOf("") }

    val people = state.context.fetch<Person>().filter { !it.isDeleted }
    val results = QuickCaptureWorkflow.pickerResults(people, query)
    val proposedName = QuickCaptureWorkflow.proposedNewName(query, suggestedName)
    val offersNew = QuickCaptureWorkflow.offersNewPerson(proposedName, results)

    KeeprSheet(onDismiss = onDismiss) {
        SheetHeader("Who?", onCancel = onDismiss)
        SearchField(query, { query = it }, "Search everyone")
        Spacer(Modifier.height(8.dp))
        if (results.isEmpty() && proposedName.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
                EmptyState("No one yet", "person.2", description = "Type a name to add someone.")
            }
            return@KeeprSheet
        }
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            if (offersNew) {
                ListRow(onClick = {
                    haptics.selection()
                    onAddNew(proposedName)
                }) {
                    IconLabel("Add “$proposedName”", "person.crop.circle.badge.plus", typography.body, colors.accent)
                }
                if (results.isNotEmpty()) Separator(start = 0.dp, end = 0.dp)
            }
            Column(verticalArrangement = Arrangement.Top) {
                results.forEachIndexed { index, person ->
                    if (index > 0) Separator(start = compactTextStart, end = 0.dp)
                    ListRow(
                        onClick = {
                            haptics.selection()
                            onPick(person)
                        },
                        verticalPadding = 8.dp,
                    ) {
                        PersonCompactRow(
                            person.displayName,
                            person.initials,
                            Modifier.weight(1f),
                            detail = person.subtitle ?: person.context.title,
                            photo = person.photoData,
                        )
                    }
                }
            }
        }
    }
}
