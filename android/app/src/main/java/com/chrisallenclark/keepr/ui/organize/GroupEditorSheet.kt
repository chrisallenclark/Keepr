package com.chrisallenclark.keepr.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.GroupRules
import com.chrisallenclark.keepr.core.domain.GroupVocabulary
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.trimmed
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.ui.components.FormTextField
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SegmentedControl
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.navigation.LocalNavigator
import com.chrisallenclark.keepr.ui.screens.ConfirmDialog
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.screens.observeStore
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing

/**
 * Create or edit a group (a "Business", "Place", "App" — whatever the user
 * calls them). Translated from Keepr/Features/People/GroupEditor.swift.
 *
 * Suggestions come first when creating, because one tap on "Gym" explains
 * what a group is for better than any paragraph. When [addingTo] is set, a
 * newly created group gets that person as its first member. [initialName]
 * and [initialAliases] prefill a new group, as Add from Contacts does with
 * shorthand it found on the cards.
 */
@Composable
fun GroupEditorSheet(
    group: PersonGroup?,
    addingTo: Person?,
    onDismiss: () -> Unit,
    mode: ContextMode = LocalNavigator.current.mode,
    onSaved: (PersonGroup) -> Unit = {},
    /** iOS `prefilledName`: a new group's starting name, e.g. shorthand found on contact cards. */
    initialName: String? = null,
    /** iOS `prefilledAlias`: a new group's starting "Also Known As". */
    initialAliases: String? = null,
) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    observeStore().value
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    val groupLabel = state.repository.preferences.string(PreferenceKey.GROUP_LABEL) ?: GroupVocabulary.default.singular
    val isEditing = group != null

    var name by rememberSaveable { mutableStateOf(group?.name ?: initialName ?: "") }
    var detail by rememberSaveable { mutableStateOf(group?.detail ?: "") }
    var aliases by rememberSaveable { mutableStateOf(group?.aliases ?: initialAliases ?: "") }
    var symbolName by rememberSaveable { mutableStateOf(group?.symbolName ?: "person.3") }
    var relationshipContext by rememberSaveable { mutableStateOf(group?.context ?: RelationshipContext.of(mode)) }
    var showsSymbolPicker by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (group == null && name.isEmpty()) runCatching { nameFocus.requestFocus() } }

    val trimmedName = name.trimmed()
    val openSuggestions = GroupRules.openSuggestions(state.context.fetch<PersonGroup>())

    fun save() {
        if (trimmedName.isEmpty()) return
        val saved = GroupRules.save(
            state.context, group, name, detail, aliases, symbolName, relationshipContext,
            addingTo = addingTo, now = state.now(),
        )
        state.save()
        haptics.success()
        onSaved(saved)
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        SheetHeader(
            if (isEditing) "Edit $groupLabel" else "New $groupLabel",
            onCancel = onDismiss,
            confirmTitle = "Save",
            confirmEnabled = trimmedName.isNotEmpty(),
            onConfirm = ::save,
        )
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            if (!isEditing && openSuggestions.isNotEmpty()) {
                GroupedSection(header = "Start From") {
                    row {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            openSuggestions.forEach { suggestion ->
                                val tint = if (trimmedName == suggestion.name) colors.accent else colors.secondaryLabel
                                IconLabel(
                                    suggestion.name,
                                    suggestion.symbolName,
                                    typography.subheadline,
                                    tint,
                                    Modifier
                                        .background(tint.copy(alpha = 0.15f), CircleShape)
                                        .clickable(remember { MutableInteractionSource() }, null) {
                                            name = suggestion.name
                                            symbolName = suggestion.symbolName
                                            relationshipContext = suggestion.context
                                            focus.clearFocus()
                                            haptics.selection()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
                SectionSpacer()
            }

            GroupedSection(footer = "A detail tells two similar ones apart — \"Delray\" under \"Life Time\".") {
                row { FormTextField(name, { name = it }, "Name", capitalization = KeyboardCapitalization.Words, focusRequester = nameFocus) }
                row { FormTextField(detail, { detail = it }, "Detail (optional)", style = typography.subheadline) }
                row { SymbolRow(symbolName) { showsSymbolPicker = true } }
            }
            SectionSpacer()

            GroupedSection(
                header = "Also Known As",
                footer = "Comma-separated. This is how importing reads your shorthand: a contact saved as \"Stanley LT Client\" lands here automatically once LT is listed. Initials are understood already — \"Life Time\" answers to \"LT\" without being told.",
            ) {
                row {
                    FormTextField(
                        aliases, { aliases = it }, "LT, LTF, Life Time Delray",
                        style = typography.subheadline,
                        capitalization = KeyboardCapitalization.Characters,
                    )
                }
            }
            SectionSpacer()

            GroupedSection(
                header = "Context",
                footer = "Both keeps it visible in Business and Personal. A gym is usually both — you train clients there and you have friends there.",
            ) {
                row {
                    ListRow {
                        SegmentedControl(
                            RelationshipContext.entries.map { it.title },
                            RelationshipContext.entries.indexOf(relationshipContext),
                            { relationshipContext = RelationshipContext.entries[it] },
                        )
                    }
                }
            }

            if (isEditing) {
                SectionSpacer()
                GroupedSection(footer = "Deleting removes only the grouping. Everyone in it stays in Keepr.") {
                    row {
                        ListRow(onClick = { confirmingDelete = true }) {
                            KText("Delete $groupLabel", color = colors.red)
                        }
                    }
                }
            }
        }
    }

    if (showsSymbolPicker) {
        SymbolPickerSheet(symbolName, { symbolName = it }, onDismiss = { showsSymbolPicker = false })
    }

    if (confirmingDelete && group != null) {
        ConfirmDialog(
            title = "Delete ${group.name}?",
            message = GroupRules.editorDeleteMessage(group.memberCount),
            confirmTitle = "Delete $groupLabel",
            onConfirm = {
                // Membership nullifies: the grouping goes, every person stays.
                state.context.delete(group)
                state.save()
                haptics.success()
                onDismiss()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}
