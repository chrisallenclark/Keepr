package com.aulorvy.keepr.ui.organize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.aulorvy.keepr.core.domain.LinkRules
import com.aulorvy.keepr.core.domain.LinkSuggester
import com.aulorvy.keepr.core.domain.LinkSuggestion
import com.aulorvy.keepr.core.domain.PeopleEngine
import com.aulorvy.keepr.core.domain.PeopleSort
import com.aulorvy.keepr.core.model.LinkRole
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.trimmed
import com.aulorvy.keepr.core.services.ContactStoreProviding
import com.aulorvy.keepr.ui.components.Avatar
import com.aulorvy.keepr.ui.components.AvatarSize
import com.aulorvy.keepr.ui.components.EmptyState
import com.aulorvy.keepr.ui.components.FormTextField
import com.aulorvy.keepr.ui.components.GroupedSection
import com.aulorvy.keepr.ui.components.IconLabel
import com.aulorvy.keepr.ui.components.KText
import com.aulorvy.keepr.ui.components.KeeprMenu
import com.aulorvy.keepr.ui.components.KeeprSheet
import com.aulorvy.keepr.ui.components.ListRow
import com.aulorvy.keepr.ui.components.MenuEntry
import com.aulorvy.keepr.ui.components.PersonCompactRow
import com.aulorvy.keepr.ui.components.SearchField
import com.aulorvy.keepr.ui.components.SectionHeader
import com.aulorvy.keepr.ui.components.SectionSpacer
import com.aulorvy.keepr.ui.components.Separator
import com.aulorvy.keepr.ui.components.SheetBody
import com.aulorvy.keepr.ui.components.SheetHeader
import com.aulorvy.keepr.ui.components.ToolbarButton
import com.aulorvy.keepr.ui.components.rememberHaptics
import com.aulorvy.keepr.ui.icons.SfSymbol
import com.aulorvy.keepr.ui.screens.LocalAppState
import com.aulorvy.keepr.ui.screens.observeStore
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Spacing
import com.aulorvy.keepr.ui.theme.medium
import com.aulorvy.keepr.ui.theme.semibold

/**
 * Connect two people — "Linda is Alex's parent". Translated from
 * Keepr/Features/Person/PersonLinkEditor.swift.
 *
 * From a profile it knows one end; from selecting two people in People it
 * knows both ([other]). When a [contactStore] is supplied and the person has a
 * contact card, the relations already written on it are offered as one-tap
 * suggestions.
 */
@Composable
fun PersonLinkEditorSheet(
    person: Person,
    onDismiss: () -> Unit,
    other: Person? = null,
    onSave: () -> Unit = {},
    contactStore: ContactStoreProviding? = null,
) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    observeStore().value
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography

    var selectedID by rememberSaveable { mutableStateOf<String?>(other?.id?.toString()) }
    var roleName by rememberSaveable { mutableStateOf("Friend") }
    var customRole by rememberSaveable { mutableStateOf("") }
    var isUsingCustomRole by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var isPickingPerson by remember { mutableStateOf(false) }
    var roleMenuOpen by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<LinkSuggestion>>(emptyList()) }

    val people = state.context.fetch<Person>()
    val candidates = LinkRules.candidates(person, people)
    val selected = selectedID?.let { id -> people.firstOrNull { it.id.toString() == id && !it.isDeleted } }
    val role = LinkRole.named(roleName)
    val resolvedRole = if (isUsingCustomRole) LinkRules.customRole(customRole) else role
    val canSave = selected != null && resolvedRole.name.isNotEmpty()

    LaunchedEffect(Unit) {
        val store = contactStore ?: return@LaunchedEffect
        val identifier = person.contactIdentifier ?: return@LaunchedEffect
        if (other != null || !store.authorizationStatus().canRead) return@LaunchedEffect
        val contact = runCatching { store.contact(identifier) }.getOrNull() ?: return@LaunchedEffect
        suggestions = LinkSuggester.suggestions(contact.relations, person, candidates)
    }

    fun apply(suggestion: LinkSuggestion) {
        selectedID = suggestion.candidate.id.toString()
        isUsingCustomRole = suggestion.role !in LinkRole.catalog
        if (isUsingCustomRole) customRole = suggestion.role.name else roleName = suggestion.role.name
        haptics.selection()
    }

    fun save() {
        val target = selected ?: return
        if (resolvedRole.name.isEmpty()) return
        LinkRules.link(state.context, person, target, resolvedRole, note, state.now())
        state.save()
        haptics.success()
        onSave()
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        if (isPickingPerson) {
            BackHandler { isPickingPerson = false }
            LinkPersonPicker(
                candidates = candidates,
                onBack = { isPickingPerson = false },
                onPick = {
                    selectedID = it.id.toString()
                    haptics.selection()
                    isPickingPerson = false
                },
            )
        } else {
            SheetHeader("Link People", onCancel = onDismiss, confirmTitle = "Save", confirmEnabled = canSave, onConfirm = ::save)
            SheetBody(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(8.dp))
                if (suggestions.isNotEmpty() && other == null) {
                    GroupedSection(header = "From Their Contact Card") {
                        suggestions.forEach { suggestion ->
                            row(16.dp + 36.dp + Spacing.medium) {
                                ListRow(onClick = { apply(suggestion) }) {
                                    Avatar(suggestion.candidate.initials, size = AvatarSize.Small, imageData = suggestion.candidate.photoData)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        KText(suggestion.candidate.displayName, style = typography.subheadline.medium)
                                        KText(suggestion.reason, style = typography.caption, color = colors.secondaryLabel, maxLines = 1)
                                    }
                                    IconLabel(suggestion.role.name, suggestion.role.symbolName, typography.caption, colors.accent)
                                }
                            }
                        }
                    }
                    SectionSpacer()
                }

                GroupedSection(header = "Who") {
                    row {
                        ListRow(onClick = { isPickingPerson = true }) {
                            KText("Person", Modifier.weight(1f))
                            if (selected != null) {
                                KText(selected.displayName, color = colors.secondaryLabel)
                            } else {
                                KText("Choose", color = colors.tertiaryLabel)
                            }
                            SfSymbol("chevron.right", colors.tertiaryLabel, typography.caption.semibold)
                        }
                    }
                }
                SectionSpacer()

                GroupedSection(header = "Relationship", footer = LinkRules.explanation(person, selected, resolvedRole)) {
                    if (isUsingCustomRole) {
                        row { FormTextField(customRole, { customRole = it }, "Relationship", capitalization = KeyboardCapitalization.Words) }
                        row {
                            ListRow(onClick = { isUsingCustomRole = false }) {
                                KText("Choose from the list", style = typography.subheadline, color = colors.accent)
                            }
                        }
                    } else {
                        row {
                            Box {
                                ListRow(onClick = { roleMenuOpen = true }) {
                                    KText("They are their…", Modifier.weight(1f))
                                    KText(role.name, color = colors.secondaryLabel)
                                    SfSymbol("chevron.down", colors.tertiaryLabel, typography.caption.semibold)
                                }
                                Box(Modifier.align(Alignment.TopEnd)) {
                                    KeeprMenu(
                                        expanded = roleMenuOpen,
                                        onDismiss = { roleMenuOpen = false },
                                        entries = LinkRole.sections.flatMapIndexed { index, section ->
                                            (if (index > 0) listOf<MenuEntry>(MenuEntry.Divider) else emptyList()) +
                                                section.roles.map { option ->
                                                    MenuEntry.Action(option.name, option.symbolName, isChecked = option.name == role.name) {
                                                        roleName = option.name
                                                    }
                                                }
                                        },
                                    )
                                }
                            }
                        }
                        row {
                            ListRow(onClick = { isUsingCustomRole = true }) {
                                KText("Type my own", style = typography.subheadline, color = colors.accent)
                            }
                        }
                    }
                }
                SectionSpacer()

                GroupedSection(footer = "Anything worth remembering about the connection itself.") {
                    row { FormTextField(note, { note = it }, "Note (optional)", style = typography.subheadline, maxLines = 5) }
                }
            }
        }
    }
}

/**
 * Choose the other end of a link, pushed inside the sheet. It can't create
 * someone new — a link needs two records that exist.
 */
@Composable
private fun LinkPersonPicker(candidates: List<Person>, onBack: () -> Unit, onPick: (Person) -> Unit) {
    val colors = KeeprTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trimmed()
    val results = PeopleEngine.sort(
        if (trimmed.isEmpty()) candidates else candidates.filter { PeopleEngine.matches(it, trimmed) },
        PeopleSort.NAME,
    )

    OrganizeHeader("Link To", leading = { ToolbarButton("chevron.left", "Back", onBack) })
    SearchField(query, { query = it }, "Search everyone")
    Spacer(Modifier.height(Spacing.small))
    if (results.isEmpty()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 360.dp), contentAlignment = Alignment.Center) {
            EmptyState(
                if (query.isEmpty()) "Nobody left to link" else "No matches",
                "person.2.slash",
                description = if (query.isEmpty()) "Everyone else is already linked to this person." else "Nothing matches \"$query\".",
            )
        }
        return
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        PeopleEngine.alphabeticalSections(results).forEach { section ->
            SectionHeader(section.key, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            section.people.forEachIndexed { index, candidate ->
                if (index > 0) Separator(start = 16.dp + 36.dp + Spacing.medium)
                ListRow(onClick = { onPick(candidate) }) {
                    PersonCompactRow(
                        candidate.displayName,
                        candidate.initials,
                        Modifier.weight(1f),
                        detail = candidate.subtitle ?: candidate.context.title,
                        photo = candidate.photoData,
                    )
                }
            }
        }
    }
}
