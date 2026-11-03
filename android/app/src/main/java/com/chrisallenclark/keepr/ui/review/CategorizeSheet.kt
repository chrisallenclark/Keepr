package com.chrisallenclark.keepr.ui.review

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.CategorySuggestion
import com.chrisallenclark.keepr.core.domain.GroupVocabulary
import com.chrisallenclark.keepr.core.domain.ReviewItem
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.model.TagKind
import com.chrisallenclark.keepr.core.persistence.CategorizeAnswer
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.core.persistence.ReviewActions
import com.chrisallenclark.keepr.ui.components.FormMenuPicker
import com.chrisallenclark.keepr.ui.components.FormTextField
import com.chrisallenclark.keepr.ui.components.FormToggle
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.ListMetrics
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SegmentedControl
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.contacts.ContactReviewSignal
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.organize.RelationshipTypesSheet
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.screens.observeStore
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * Place one person: what they are to you, where they came from, and how you
 * met — asked while the answer is still in your head. Translated from
 * Keepr/Features/Review/CategorizeSheet.swift.
 *
 * "How did you meet?" sits here and nowhere else in the flow because it's the
 * fact with the shortest half-life.
 */
@Composable
fun CategorizeSheet(item: ReviewItem, mode: ContextMode, onDismiss: () -> Unit, onSaved: () -> Unit = {}) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    observeStore().value

    val tags = state.context.fetch<RelationshipTag>().sortedBy { it.sortOrder }
    val groups = state.context.fetch<PersonGroup>().sortedBy { it.sortOrder }
    val groupLabel = state.repository.preferences.string(PreferenceKey.GROUP_LABEL) ?: GroupVocabulary.default.singular
    val groupPlural = GroupVocabulary.plural(groupLabel)

    val existing = (item.subject as? ReviewItem.Subject.Existing)?.person

    var relationshipContext by remember(item.id) {
        mutableStateOf(existing?.context ?: item.suggestion?.context ?: RelationshipContext.of(mode))
    }
    var selectedTagID by remember(item.id) {
        val initialKind = kindFor(existing?.context ?: item.suggestion?.context ?: RelationshipContext.of(mode))
        val fromPerson = existing?.tagList?.firstOrNull()?.id
        val fromSuggestion = item.suggestion?.tagName?.let { name ->
            tags.filter { it.kind == initialKind }.firstOrNull { it.builtInKey == name || it.name == name }?.id
        }
        mutableStateOf(fromPerson ?: fromSuggestion)
    }
    var selectedGroupIDs by remember(item.id) {
        // Groups read off the contact card start ticked, since that's what the
        // card said — visible and untickable, not applied behind their back.
        val fromPerson = existing?.groupList?.map { it.id }.orEmpty()
        val fromCard = item.suggestion?.groupIDs.orEmpty().mapNotNull { id ->
            groups.firstOrNull { it.id.toString().uppercase() == id }?.id
        }
        mutableStateOf((fromPerson + fromCard).toSet())
    }
    var howWeMet by remember(item.id) { mutableStateOf(existing?.howWeMet ?: "") }
    var memoryText by remember(item.id) { mutableStateOf("") }
    var interactionNote by remember(item.id) { mutableStateOf("") }
    var interactionKind by remember(item.id) { mutableStateOf(InteractionKind.IN_PERSON) }
    var isLoggingInteraction by remember(item.id) { mutableStateOf(false) }
    var isShowingTypeEditor by remember { mutableStateOf(false) }

    val kind = kindFor(relationshipContext)
    val relevantTags = tags.filter { it.kind == kind }
    val relevantGroups = when (relationshipContext) {
        RelationshipContext.BOTH -> groups
        RelationshipContext.BUSINESS -> groups.filter { it.matches(ContextMode.BUSINESS) }
        RelationshipContext.PERSONAL -> groups.filter { it.matches(ContextMode.PERSONAL) }
    }

    fun applySuggestion(suggestion: CategorySuggestion) {
        relationshipContext = suggestion.context
        val name = suggestion.tagName
        if (name != null) {
            val suggestedKind = kindFor(suggestion.context)
            selectedTagID = tags.firstOrNull { (it.builtInKey == name || it.name == name) && it.kind == suggestedKind }?.id
        }
        haptics.selection()
    }

    fun metAt(group: PersonGroup) = "Met at ${group.name}"

    fun toggle(group: PersonGroup) {
        if (group.id in selectedGroupIDs) {
            selectedGroupIDs = selectedGroupIDs - group.id
            // Only take back the sentence this screen wrote itself.
            if (howWeMet == metAt(group)) howWeMet = ""
        } else {
            selectedGroupIDs = selectedGroupIDs + group.id
            // A first draft, in an editable field, on the one screen that's
            // actually asking the question. Never overwrites what's there.
            if (howWeMet.isEmpty()) howWeMet = metAt(group)
        }
        haptics.selection()
    }

    fun save() {
        ReviewActions.categorize(
            item,
            CategorizeAnswer(
                context = relationshipContext,
                tag = selectedTagID?.let { id -> tags.firstOrNull { it.id == id } },
                groups = groups.filter { it.id in selectedGroupIDs },
                howWeMet = howWeMet,
                memory = memoryText,
                interactionKind = if (isLoggingInteraction) interactionKind else null,
                interactionNote = interactionNote,
            ),
            state.context,
            state.contactTracker,
            state.calendar,
            state.now(),
        )
        state.save()
        ContactReviewSignal.changed()
        haptics.success()
        onSaved()
        onDismiss()
    }

    KeeprSheet(onDismiss = onDismiss) {
        SheetHeader(
            if (item.isNew) "New Contact" else "Categorize",
            onCancel = onDismiss,
            confirmTitle = "Save",
            onConfirm = ::save,
        )
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            // The person's name heads the first section, in the primary color.
            KText(
                item.name,
                style = typography.headline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ListMetrics.sectionInset + ListMetrics.textInset, end = ListMetrics.sectionInset, bottom = 9.dp),
            )
            GroupedSection(footer = item.detail) {
                row {
                    ListRow {
                        SegmentedControl(
                            RelationshipContext.entries.map { it.title },
                            RelationshipContext.entries.indexOf(relationshipContext),
                            {
                                relationshipContext = RelationshipContext.entries[it]
                                haptics.selection()
                            },
                        )
                    }
                }
            }

            item.suggestion?.let { suggestion ->
                SectionSpacer()
                GroupedSection {
                    row {
                        ListRow(onClick = { applySuggestion(suggestion) }) {
                            SfSymbol("wand.and.stars", colors.accent, typography.body)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                KText("Use ${suggestion.tagName ?: suggestion.context.title}", color = colors.accent)
                                KText(suggestion.reason, style = typography.caption, color = colors.secondaryLabel)
                            }
                        }
                    }
                }
            }

            SectionSpacer()
            GroupedSection(header = "Relationship Type") {
                row {
                    ListRow(onClick = { selectedTagID = null; haptics.selection() }) {
                        KText("None", Modifier.weight(1f))
                        if (selectedTagID == null) SfSymbol("checkmark", colors.accent, typography.body.semibold)
                    }
                }
                relevantTags.forEach { tag ->
                    row {
                        ListRow(onClick = { selectedTagID = tag.id; haptics.selection() }) {
                            IconLabel(tag.name, tag.symbolName, typography.body, colors.label, Modifier.weight(1f))
                            if (selectedTagID == tag.id) SfSymbol("checkmark", colors.accent, typography.body.semibold)
                        }
                    }
                }
                row {
                    ListRow(onClick = { isShowingTypeEditor = true }) {
                        IconLabel("New Type…", "plus.circle", typography.subheadline, colors.accent)
                    }
                }
            }

            if (relevantGroups.isNotEmpty()) {
                SectionSpacer()
                GroupedSection(
                    header = groupPlural,
                    footer = "Where the relationship comes through — a gym, one of your businesses, an app, a night out.",
                ) {
                    relevantGroups.forEach { group ->
                        row {
                            ListRow(onClick = { toggle(group) }) {
                                IconLabel(group.name, group.symbolName, typography.body, colors.label, Modifier.weight(1f))
                                if (group.id in selectedGroupIDs) SfSymbol("checkmark", colors.accent, typography.body.semibold)
                            }
                        }
                    }
                }
            }

            SectionSpacer()
            GroupedSection(header = "How You Met", footer = "The first thing to fade. Worth ten seconds now.") {
                row { FormTextField(howWeMet, { howWeMet = it }, "Met at the gym, introduced by Michael…", maxLines = 4) }
            }

            SectionSpacer()
            GroupedSection(
                header = "Worth Remembering",
                footer = "Whatever they told you. This becomes the context on their profile, and it's searchable.",
            ) {
                row {
                    FormTextField(
                        memoryText,
                        { memoryText = it },
                        "Runs a roofing crew, wants to lose 20 lb before his daughter's wedding…",
                        minLines = 2,
                        maxLines = 6,
                    )
                }
            }

            SectionSpacer()
            GroupedSection(
                footer = "Records that you spoke today, which is also what starts the clock on how often you reach out to them.",
            ) {
                row { FormToggle("Log this as an interaction", isLoggingInteraction, { isLoggingInteraction = it }) }
                if (isLoggingInteraction) {
                    row {
                        FormMenuPicker("How", InteractionKind.entries, interactionKind, { it.title }, { interactionKind = it }, symbol = { it.symbolName })
                    }
                    row { FormTextField(interactionNote, { interactionNote = it }, "What happened", maxLines = 4) }
                }
            }
        }
    }

    if (isShowingTypeEditor) {
        RelationshipTypesSheet(onDismiss = { isShowingTypeEditor = false })
    }
}

/** Both-context people pick from business types, as on iOS. */
private fun kindFor(context: RelationshipContext): TagKind =
    if (context == RelationshipContext.PERSONAL) TagKind.PERSONAL else TagKind.BUSINESS
