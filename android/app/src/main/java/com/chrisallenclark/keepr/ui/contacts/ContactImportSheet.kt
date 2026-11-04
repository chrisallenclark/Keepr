package com.chrisallenclark.keepr.ui.contacts

import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.CategorySuggestion
import com.chrisallenclark.keepr.core.domain.ContactMarkerParser
import com.chrisallenclark.keepr.core.domain.GroupVocabulary
import com.chrisallenclark.keepr.core.domain.MarkerVocabulary
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.model.nonBlank
import com.chrisallenclark.keepr.core.persistence.ContactImport
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.core.services.ContactAccess
import com.chrisallenclark.keepr.core.services.ContactSummary
import com.chrisallenclark.keepr.ui.components.Avatar
import com.chrisallenclark.keepr.ui.components.AvatarSize
import com.chrisallenclark.keepr.ui.components.EmptyState
import com.chrisallenclark.keepr.ui.components.FormTextField
import com.chrisallenclark.keepr.ui.components.FormToggle
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprButton
import com.chrisallenclark.keepr.ui.components.KeeprButtonStyle
import com.chrisallenclark.keepr.ui.components.KeeprMenu
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.ListMetrics
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.MenuEntry
import com.chrisallenclark.keepr.ui.components.SearchField
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SegmentedControl
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.ToolbarButton
import com.chrisallenclark.keepr.ui.components.ToolbarTextButton
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.screens.observeStore
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.medium
import kotlinx.coroutines.launch

/**
 * Pick people out of the phone's contacts and bring them in as relationships.
 * Translated from Keepr/Features/People/ContactImportView.swift.
 *
 * Every permission state is a normal state, not an error: not-yet-asked offers
 * the prompt; denied points to Settings and leaves the manual path open. The
 * sheet asks for permission itself, so People's empty state and onboarding can
 * simply present it.
 *
 * A contact already linked to someone in Keepr shows "Added" and can't be
 * picked, so importing twice never duplicates anyone. As on iOS, a person typed
 * in by hand with the same name is not assumed to be the same person.
 */
@Composable
fun ContactImportSheet(mode: ContextMode, onDismiss: () -> Unit) {
    val state = LocalAppState.current
    val store = state.contacts
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    observeStore().value

    ContactPermissionHost(store)

    val prefs = state.repository.preferences
    val groupLabel = prefs.string(PreferenceKey.GROUP_LABEL) ?: GroupVocabulary.default.singular
    val groups = state.context.fetch<PersonGroup>().sortedBy { it.sortOrder }
    val linked = ContactImport.linkedIdentifiers(state.context.fetch<Person>())

    var access by remember { mutableStateOf(store.authorizationStatus()) }
    var contacts by remember { mutableStateOf<List<ContactSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var suggestions by remember { mutableStateOf<Map<String, CategorySuggestion>>(emptyMap()) }
    var discovered by remember { mutableStateOf<List<ContactMarkerParser.Candidate>>(emptyList()) }
    var newGroupToken by remember { mutableStateOf<String?>(null) }
    var applySuggestions by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    // Starts as loading when contacts can be read, so the first frame isn't "No contacts".
    var isLoading by remember { mutableStateOf(store.authorizationStatus().canRead) }
    var optionsOpen by remember { mutableStateOf(false) }

    fun vocabulary(): MarkerVocabulary = MarkerVocabulary.build(
        state.context.fetch<PersonGroup>().sortedBy { it.sortOrder },
        state.context.fetch<RelationshipTag>().sortedBy { it.sortOrder },
    )

    /**
     * Re-read every card against the current vocabulary. Runs again whenever a
     * group is made from discovered shorthand, so the list visibly reorganizes
     * the moment "LT" means something.
     */
    fun rebuildSuggestions() {
        val vocabulary = vocabulary()
        suggestions = ContactImport.suggestions(contacts, vocabulary)
        discovered = ContactMarkerParser.candidates(contacts, vocabulary)
    }

    suspend fun load() {
        access = store.authorizationStatus()
        if (access.canRead && contacts.isEmpty()) {
            isLoading = true
            contacts = store.fetchContacts()
            rebuildSuggestions()
        }
        isLoading = false
    }

    LaunchedEffect(Unit) { load() }

    val results = ContactImport.results(contacts, query)
    val withSuggestions = ContactImport.importableWithSuggestions(results, suggestions, linked)

    fun toggle(contact: ContactSummary) {
        selected = if (contact.identifier in selected) selected - contact.identifier else selected + contact.identifier
        haptics.selection()
    }

    fun importSelected() {
        val chosen = contacts.filter { it.identifier in selected }
        ContactImport.importContacts(
            chosen, suggestions, applySuggestions, mode, state.context, groups, state.calendar, state.now(),
        )
        state.save()
        // Anyone imported here has been dealt with, so Catch Up doesn't raise
        // them again as a new contact tomorrow.
        state.contactTracker.acknowledge(chosen.map { it.identifier })
        ContactReviewSignal.changed()
        haptics.success()
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxHeight()) {
            if (access.canRead && !isLoading) {
                SheetToolbar(
                    "Add from Contacts",
                    leading = { ToolbarTextButton("Cancel", onDismiss) },
                    trailing = {
                        Box {
                            ToolbarButton("ellipsis", "Options", { optionsOpen = true })
                            KeeprMenu(
                                expanded = optionsOpen,
                                onDismiss = { optionsOpen = false },
                                entries = buildList {
                                    add(MenuEntry.Action("Select All", "checkmark.circle") {
                                        selected = ContactImport.selectAll(results, linked)
                                        haptics.selection()
                                    })
                                    if (withSuggestions.isNotEmpty()) {
                                        add(MenuEntry.Action("Select ${withSuggestions.size} with Suggestions", "wand.and.stars") {
                                            selected = withSuggestions.map { it.identifier }.toSet()
                                            haptics.selection()
                                        })
                                    }
                                    if (selected.isNotEmpty()) {
                                        add(MenuEntry.Action("Deselect All", "circle") {
                                            selected = emptySet()
                                            haptics.selection()
                                        })
                                    }
                                },
                            )
                        }
                        ToolbarTextButton("Add ${selected.size}", ::importSelected, isProminent = true, enabled = selected.isNotEmpty())
                    },
                )
            } else {
                SheetHeader("Add from Contacts", onCancel = onDismiss)
            }

            when {
                isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingState("Reading contacts", Modifier.padding(bottom = 60.dp))
                }
                access.canRead -> {
                    SearchField(query, { query = it }, "Search contacts")
                    Spacer(Modifier.height(10.dp))
                    if (results.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                if (query.isEmpty()) "No contacts" else "No matches",
                                "person.crop.circle",
                                description = if (query.isEmpty()) "There's nothing in your contacts to import."
                                else "Nothing matches \"$query\".",
                                modifier = Modifier.padding(bottom = 60.dp),
                            )
                        }
                    } else {
                        ContactList(
                            access = access,
                            results = results,
                            linked = linked,
                            selected = selected,
                            suggestions = suggestions,
                            discovered = discovered,
                            applySuggestions = applySuggestions,
                            groupLabel = groupLabel,
                            onApplySuggestionsChange = { applySuggestions = it },
                            onToggle = ::toggle,
                            onDiscovered = { newGroupToken = it },
                            onOpenSettings = { openAppSettings(context) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        "Contacts are off",
                        "person.crop.circle.badge.exclamationmark",
                        description = access.explanation ?: "Keepr needs contact access to import people.",
                        modifier = Modifier.padding(bottom = 60.dp),
                    ) {
                        if (access == ContactAccess.NOT_DETERMINED) {
                            KeeprButton(
                                "Allow Contacts",
                                {
                                    scope.launch {
                                        access = store.requestAccess()
                                        load()
                                    }
                                },
                                style = KeeprButtonStyle.Prominent,
                            )
                        } else {
                            KeeprButton("Open Settings", { openAppSettings(context) }, style = KeeprButtonStyle.Bordered)
                        }
                    }
                }
            }
        }
    }

    newGroupToken?.let { token ->
        // Prefilled with the shorthand as both the name and the alias, so "LT"
        // keeps working even after it's renamed to "Life Time".
        ShorthandGroupSheet(
            token = token,
            mode = mode,
            groupLabel = groupLabel,
            onDismiss = { newGroupToken = null },
            onSaved = { rebuildSuggestions() },
        )
    }
}

@Composable
private fun ContactList(
    access: ContactAccess,
    results: List<ContactSummary>,
    linked: Set<String>,
    selected: Set<String>,
    suggestions: Map<String, CategorySuggestion>,
    discovered: List<ContactMarkerParser.Candidate>,
    applySuggestions: Boolean,
    groupLabel: String,
    onApplySuggestionsChange: (Boolean) -> Unit,
    onToggle: (ContactSummary) -> Unit,
    onDiscovered: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    // Tagged so device tests can scroll this list rather than a chip row.
    LazyColumn(modifier.fillMaxWidth().testTag("contactList")) {
        if (access == ContactAccess.LIMITED) {
            item(key = "limited") {
                GroupedSection {
                    row {
                        ListRow(onClick = onOpenSettings) {
                            SfSymbol("info.circle", colors.secondaryLabel, typography.footnote)
                            KText(
                                "You're sharing some contacts with Keepr. Tap to share more in Settings.",
                                style = typography.footnote,
                                color = colors.secondaryLabel,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                SectionSpacer()
            }
        }

        // Shorthand that's all over the address book but means nothing to Keepr
        // yet — "LT" on eight cards. One tap turns a habit the user already has
        // into something the importer can act on.
        if (discovered.isNotEmpty()) {
            item(key = "discovered") {
                GroupedSection(
                    header = "Shorthand On Your Contacts",
                    footer = "Keepr keeps seeing these on your contact cards. Make one a ${groupLabel.lowercase()} and every contact carrying it gets sorted automatically — now and every time you import.",
                ) {
                    row {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = ListMetrics.rowHorizontal, top = 8.dp, bottom = 8.dp, end = ListMetrics.rowHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        ) {
                            discovered.forEach { candidate -> ShorthandChip(candidate) { onDiscovered(candidate.token) } }
                        }
                    }
                }
                SectionSpacer()
            }
        }

        if (suggestions.isNotEmpty()) {
            item(key = "apply") {
                GroupedSection(
                    footer = "Read off each contact card — your own shorthand first, then an employer, a job title, a family relation. Everything is editable afterwards, and anything Keepr can't place is left for you.",
                ) {
                    row { FormToggle("Apply suggested categories", applySuggestions, onApplySuggestionsChange) }
                }
                SectionSpacer()
            }
        }

        itemsIndexed(results, key = { _, contact -> contact.identifier }) { index, contact ->
            InsetGroupedRow(index, results.size, separatorStart = avatarTextStart) {
                ContactRow(
                    contact = contact,
                    suggestion = suggestions[contact.identifier],
                    isLinked = contact.identifier in linked,
                    isSelected = contact.identifier in selected,
                    applySuggestions = applySuggestions,
                    onToggle = { onToggle(contact) },
                )
            }
        }
        item(key = "bottom") { ListBottomClearance() }
    }
}

@Composable
private fun ShorthandChip(candidate: ContactMarkerParser.Candidate, onClick: () -> Unit) {
    val colors = KeeprTheme.colors
    val style = KeeprTheme.typography.subheadline
    Row(
        Modifier
            .background(colors.tertiaryFill, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .semantics { contentDescription = "Make ${candidate.token} a group, on ${candidate.count} contacts" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        SfSymbol("plus.circle", colors.accent, style)
        KText(candidate.token, style = style.medium, color = colors.accent, maxLines = 1)
        KText("${candidate.count}", style = style, color = colors.secondaryLabel, maxLines = 1)
    }
}

@Composable
private fun ContactRow(
    contact: ContactSummary,
    suggestion: CategorySuggestion?,
    isLinked: Boolean,
    isSelected: Boolean,
    applySuggestions: Boolean,
    onToggle: () -> Unit,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    ListRow(onClick = if (isLinked) null else onToggle) {
        Avatar(contact.initials, size = AvatarSize.Small, imageData = contact.thumbnailData)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(
                ContactImport.displayName(contact, suggestion, applySuggestions),
                style = typography.subheadline.medium,
                color = if (isLinked) colors.secondaryLabel else colors.label,
            )
            contact.subtitle?.let {
                KText(it, style = typography.caption, color = colors.secondaryLabel, maxLines = 1)
            }
            if (applySuggestions && suggestion != null && !isLinked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
                    SfSymbol(suggestion.context.symbolName, colors.accent, typography.caption2.medium)
                    KText(ContactImport.suggestionLabel(suggestion), style = typography.caption2.medium, color = colors.accent, maxLines = 1)
                }
                KText(suggestion.reason, style = typography.caption2, color = colors.tertiaryLabel, maxLines = 1)
            }
        }
        when {
            isLinked -> KText("Added", style = typography.caption, color = colors.tertiaryLabel)
            isSelected -> SfSymbol("checkmark.circle.fill", colors.accent, 22.dp, contentDescription = "Selected")
            else -> SfSymbol("circle", colors.quaternaryLabel, 22.dp, contentDescription = "Not selected")
        }
    }
}

/**
 * A group made from shorthand found on the cards — `GroupEditor` opened with
 * `prefilledName` and `prefilledAlias`. Only what a new group needs: a symbol
 * can be chosen later from the group's own editor.
 */
@Composable
private fun ShorthandGroupSheet(
    token: String,
    mode: ContextMode,
    groupLabel: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    var name by remember { mutableStateOf(token) }
    var detail by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf(token) }
    var relationshipContext by remember { mutableStateOf(RelationshipContext.of(mode)) }
    val trimmedName = name.trim()

    fun save() {
        val existing = state.context.fetch<PersonGroup>()
        state.context.insert(
            PersonGroup(
                name = trimmedName,
                symbolName = "person.3",
                detail = detail.nonBlank(),
                aliases = aliases.nonBlank(),
                context = relationshipContext,
                sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: 0) + 10,
            ),
        )
        state.save()
        haptics.success()
        onSaved()
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        SheetHeader(
            "New $groupLabel",
            onCancel = onDismiss,
            confirmTitle = "Save",
            confirmEnabled = trimmedName.isNotEmpty(),
            onConfirm = ::save,
        )
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            GroupedSection(footer = "A detail tells two similar ones apart — \"Delray\" under \"Life Time\".") {
                row { FormTextField(name, { name = it }, "Name", capitalization = KeyboardCapitalization.Words) }
                row { FormTextField(detail, { detail = it }, "Detail (optional)", style = KeeprTheme.typography.subheadline) }
            }
            SectionSpacer()
            GroupedSection(
                header = "Also Known As",
                footer = "Comma-separated. This is how importing reads your shorthand: a contact saved as \"Stanley LT Client\" lands here automatically once LT is listed. Initials are understood already — \"Life Time\" answers to \"LT\" without being told.",
            ) {
                row {
                    FormTextField(
                        aliases,
                        { aliases = it },
                        "LT, LTF, Life Time Delray",
                        style = KeeprTheme.typography.subheadline,
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
        }
    }
}
