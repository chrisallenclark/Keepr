package com.chrisallenclark.keepr.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.CadenceEngine
import com.chrisallenclark.keepr.core.domain.RelationshipTypeRules
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.model.TagKind
import com.chrisallenclark.keepr.core.model.trimmed
import com.chrisallenclark.keepr.ui.components.ContextSwitcher
import com.chrisallenclark.keepr.ui.components.FormMenuPicker
import com.chrisallenclark.keepr.ui.components.FormTextField
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SegmentedControl
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.SwipeAction
import com.chrisallenclark.keepr.ui.components.SwipeActionsRow
import com.chrisallenclark.keepr.ui.components.ToolbarButton
import com.chrisallenclark.keepr.ui.components.ToolbarTextButton
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.navigation.LocalNavigator
import com.chrisallenclark.keepr.ui.screens.ConfirmDialog
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.screens.observeStore
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Radius
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * Manage relationship types. Translated from
 * Keepr/Features/People/RelationshipTypeEditor.swift.
 *
 * The built-in list is a starting point, not a taxonomy: anything here can be
 * renamed, re-symbolled, or deleted — built-ins included. [startsCreating]
 * opens straight into the new-type form, for "New Type…" in Edit Person.
 */
@Composable
fun RelationshipTypesSheet(
    onDismiss: () -> Unit,
    startsCreating: Boolean = false,
    initialMode: ContextMode = LocalNavigator.current.mode,
    /**
     * Whether switching Business/Personal here switches the whole app, as
     * `RelationshipTypeEditor(mode: $mode)` does from People and Settings.
     * Edit Person and Categorize pass `.constant(mode)` on iOS, so false.
     */
    setsAppMode: Boolean = true,
) {
    val navigator = LocalNavigator.current
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    observeStore().value
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography

    var mode by remember { mutableStateOf(initialMode) }
    fun switchMode(to: ContextMode) {
        mode = to
        if (setsAppMode) navigator.setMode(to)
    }
    val kind = TagKind.of(mode)
    val allTags = state.context.fetch<RelationshipTag>().sortedBy { it.sortOrder }
    val tags = RelationshipTypeRules.types(kind, allTags)

    var isCreating by remember { mutableStateOf(false) }
    var draftName by rememberSaveable { mutableStateOf("") }
    var draftSymbolName by rememberSaveable { mutableStateOf("tag") }
    var draftKind by rememberSaveable { mutableStateOf(kind) }
    var duplicateWarning by remember { mutableStateOf<String?>(null) }
    var showsSymbolPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RelationshipTag?>(null) }
    var pendingDeletion by remember { mutableStateOf<RelationshipTag?>(null) }
    val draftFocus = remember { FocusRequester() }

    fun startCreating() {
        draftName = ""
        draftSymbolName = "tag"
        draftKind = kind
        duplicateWarning = null
        isCreating = true
    }

    fun cancelCreating() {
        focus.clearFocus()
        duplicateWarning = null
        isCreating = false
    }

    fun create() {
        val name = draftName.trimmed()
        if (name.isEmpty()) return
        val created = RelationshipTypeRules.create(state.context, name, draftKind, draftSymbolName, state.now())
        if (created == null) {
            duplicateWarning = RelationshipTypeRules.duplicateWarning(name, draftKind)
            return
        }
        state.save()
        haptics.success()
        // Follow the new type into its list, so it isn't created into a
        // screen the user isn't looking at.
        if (draftKind != kind) switchMode(if (draftKind == TagKind.BUSINESS) ContextMode.BUSINESS else ContextMode.PERSONAL)
        focus.clearFocus()
        isCreating = false
    }

    LaunchedEffect(Unit) { if (startsCreating && !isCreating) startCreating() }
    LaunchedEffect(isCreating) { if (isCreating) runCatching { draftFocus.requestFocus() } }

    KeeprSheet(onDismiss = onDismiss) {
        OrganizeHeader(
            "Relationship Types",
            trailing = {
                ToolbarButton(
                    "plus",
                    "New Type",
                    { if (!isCreating) startCreating() },
                    tint = if (isCreating) colors.tertiaryLabel else colors.label,
                )
                ToolbarTextButton("Done", onDismiss, isProminent = true)
            },
        )
        ContextSwitcher(mode == ContextMode.PERSONAL, { switchMode(if (it) ContextMode.PERSONAL else ContextMode.BUSINESS) })
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            if (isCreating) {
                GroupedSection(header = "New Type") {
                    row {
                        FormTextField(
                            draftName,
                            { draftName = it; duplicateWarning = null },
                            "Name",
                            capitalization = KeyboardCapitalization.Words,
                            focusRequester = draftFocus,
                        )
                    }
                    row { SymbolRow(draftSymbolName) { showsSymbolPicker = true } }
                    row {
                        ListRow {
                            SegmentedControl(
                                TagKind.entries.map { it.title },
                                TagKind.entries.indexOf(draftKind),
                                { draftKind = TagKind.entries[it]; duplicateWarning = null },
                            )
                        }
                    }
                    duplicateWarning?.let { warning -> row { WarningRow(warning) } }
                    row {
                        ListRow {
                            KeeprButton("Cancel", ::cancelCreating)
                            Spacer(Modifier.weight(1f))
                            KeeprButton(
                                "Add",
                                ::create,
                                enabled = draftName.trimmed().isNotEmpty(),
                                textStyle = typography.body.semibold,
                            )
                        }
                    }
                }
                SectionSpacer()
            }

            GroupedSection(
                header = "${kind.title} Types",
                footer = "Tap a type to rename it or change its symbol. Swipe to delete — including the ones Keepr started you with. Deleting a type never deletes anyone.",
            ) {
                tags.forEach { tag ->
                    row(16.dp + 26.dp + Spacing.medium) {
                        SwipeActionsRow(
                            // Full swipe is off: deletion asks first.
                            trailing = listOf(SwipeAction("Delete", "trash", colors.red) { pendingDeletion = tag }),
                            allowsFullSwipe = false,
                        ) {
                            ListRow(onClick = { editing = tag }) {
                                Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                                    SfSymbol(tag.symbolName, colors.accent, typography.body)
                                }
                                KText(tag.name, Modifier.weight(1f), maxLines = 1)
                                if (tag.isBuiltIn) {
                                    KText(
                                        "Built-in",
                                        Modifier
                                            .background(colors.quaternaryLabel, RoundedCornerShape(Radius.chip))
                                            .padding(horizontal = Spacing.small, vertical = 3.dp),
                                        style = typography.caption2,
                                        color = colors.secondaryLabel,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showsSymbolPicker) {
        SymbolPickerSheet(draftSymbolName, { draftSymbolName = it }, onDismiss = { showsSymbolPicker = false })
    }
    editing?.let { tag ->
        RelationshipTypeDetailSheet(tag, allTags, onDismiss = { editing = null })
    }
    pendingDeletion?.let { tag ->
        ConfirmDialog(
            title = "Delete ${tag.name}?",
            message = RelationshipTypeRules.deleteMessage(tag.peopleList.size),
            confirmTitle = "Delete Type",
            onConfirm = {
                // People are unmarked, never removed. Seeding remembers the
                // built-in was offered, so it doesn't come back next launch.
                state.context.delete(tag)
                state.save()
                haptics.success()
            },
            onDismiss = { pendingDeletion = null },
        )
    }
}

/**
 * Rename, re-symbol and set the rhythm of one type. iOS
 * `RelationshipTypeDetailEditor`. Built-ins are editable too.
 */
@Composable
private fun RelationshipTypeDetailSheet(
    tag: RelationshipTag,
    existingTags: List<RelationshipTag>,
    onDismiss: () -> Unit,
) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    var name by rememberSaveable { mutableStateOf(tag.name) }
    var symbolName by rememberSaveable { mutableStateOf(tag.symbolName) }
    var cadenceDays by rememberSaveable { mutableStateOf(tag.cadenceDays ?: 0) }
    var showsSymbolPicker by remember { mutableStateOf(false) }
    var duplicateWarning by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (name.trimmed().isEmpty()) return
        if (!RelationshipTypeRules.update(tag, name, symbolName, cadenceDays, existingTags)) {
            duplicateWarning = RelationshipTypeRules.duplicateWarning(name, tag.kind)
            return
        }
        state.save()
        haptics.success()
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        SheetHeader(
            "${tag.kind.title} Type",
            onCancel = onDismiss,
            confirmTitle = "Save",
            confirmEnabled = name.trimmed().isNotEmpty(),
            onConfirm = ::save,
        )
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            GroupedSection(
                footer = if (tag.isBuiltIn) "One of the types Keepr started you with. Rename it, re-symbol it, or delete it from the list — it's yours." else null,
            ) {
                row {
                    FormTextField(
                        name,
                        { name = it; duplicateWarning = null },
                        "Name",
                        capitalization = KeyboardCapitalization.Words,
                    )
                }
                row { SymbolRow(symbolName) { showsSymbolPicker = true } }
                duplicateWarning?.let { warning -> row { WarningRow(warning) } }
            }
            SectionSpacer()
            GroupedSection(
                header = "Rhythm",
                footer = "Set once here and it applies to everyone with this type. Today tells you who's gone past it, and logging any interaction resets that person's clock. Anyone who's different can be set on their own profile.",
            ) {
                row {
                    FormMenuPicker(
                        "Reach out",
                        listOf(0) + CadenceEngine.presets,
                        cadenceDays,
                        { days -> if (days == 0) "No rhythm" else CadenceEngine.label(days) },
                        { cadenceDays = it },
                    )
                }
            }
        }
    }

    if (showsSymbolPicker) {
        SymbolPickerSheet(symbolName, { symbolName = it }, onDismiss = { showsSymbolPicker = false })
    }
}
