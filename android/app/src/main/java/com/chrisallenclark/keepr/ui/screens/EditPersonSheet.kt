package com.chrisallenclark.keepr.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.Priority
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.model.TagKind
import com.chrisallenclark.keepr.core.model.nonBlank
import com.chrisallenclark.keepr.core.model.trimmedSpaces
import com.chrisallenclark.keepr.ui.components.FormMenuPicker
import com.chrisallenclark.keepr.ui.components.FormTextField
import com.chrisallenclark.keepr.ui.components.FormToggle
import com.chrisallenclark.keepr.ui.components.GroupedSection
import com.chrisallenclark.keepr.ui.components.IconLabel
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.ListRow
import com.chrisallenclark.keepr.ui.components.SectionSpacer
import com.chrisallenclark.keepr.ui.components.SegmentedControl
import com.chrisallenclark.keepr.ui.components.SheetBody
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.semibold
import java.util.UUID

/**
 * Add or edit a person. Translated from Keepr/Features/People/EditPersonView.swift.
 */
@Composable
fun EditPersonSheet(person: Person?, mode: ContextMode, onDismiss: () -> Unit, onSaved: (Person) -> Unit = {}) {
    val state = LocalAppState.current
    val haptics = rememberHaptics()
    observeStore().value
    val allTags = state.context.fetch<RelationshipTag>().sortedBy { it.sortOrder }
    var isShowingTypeEditor by remember { mutableStateOf(false) }

    var givenName by rememberSaveable { mutableStateOf(person?.givenName ?: "") }
    var familyName by rememberSaveable { mutableStateOf(person?.familyName ?: "") }
    var preferredName by rememberSaveable { mutableStateOf(person?.preferredName ?: "") }
    var company by rememberSaveable { mutableStateOf(person?.company ?: "") }
    var jobTitle by rememberSaveable { mutableStateOf(person?.jobTitle ?: "") }
    var workNote by rememberSaveable { mutableStateOf(person?.workNote ?: "") }
    var phone by rememberSaveable { mutableStateOf(person?.primaryPhone ?: "") }
    var email by rememberSaveable { mutableStateOf(person?.primaryEmail ?: "") }
    var relationshipContext by rememberSaveable { mutableStateOf(person?.context ?: RelationshipContext.of(mode)) }
    var priority by rememberSaveable { mutableStateOf(person?.priority ?: Priority.NORMAL) }
    var isFavorite by rememberSaveable { mutableStateOf(person?.isFavorite ?: false) }
    var howWeMet by rememberSaveable { mutableStateOf(person?.howWeMet ?: "") }
    var introducedBy by rememberSaveable { mutableStateOf(person?.introducedBy ?: "") }
    var notes by rememberSaveable { mutableStateOf(person?.notes ?: "") }
    var selectedTagIDs by remember { mutableStateOf(person?.tagList?.map { it.id }?.toSet() ?: emptySet<UUID>()) }
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (person == null) runCatching { nameFocus.requestFocus() } }

    val canSave = givenName.trimmedSpaces().isNotEmpty() || familyName.trimmedSpaces().isNotEmpty() || preferredName.trimmedSpaces().isNotEmpty()

    val relevantTags = when (relationshipContext) {
        RelationshipContext.BUSINESS -> allTags.filter { it.kind == TagKind.BUSINESS }
        RelationshipContext.PERSONAL -> allTags.filter { it.kind == TagKind.PERSONAL }
        RelationshipContext.BOTH -> allTags
    }

    fun save() {
        val target = person ?: Person(context = relationshipContext, createdAt = state.now()).also { state.context.insert(it) }
        target.givenName = givenName.trimmedSpaces()
        target.familyName = familyName.trimmedSpaces()
        target.preferredName = preferredName.nonBlank()
        target.company = company.nonBlank()
        target.jobTitle = jobTitle.nonBlank()
        target.workNote = workNote.nonBlank()
        target.phoneNumbers = phone.nonBlank()?.let { listOf(it) } ?: emptyList()
        target.emailAddresses = email.nonBlank()?.let { listOf(it) } ?: emptyList()
        target.context = relationshipContext
        target.priority = priority
        target.isFavorite = isFavorite
        target.howWeMet = howWeMet.nonBlank()
        target.introducedBy = introducedBy.nonBlank()
        target.notes = notes.nonBlank()
        target.tags = allTags.filter { it.id in selectedTagIDs }
        target.touch(state.now())
        state.save()
        haptics.success()
        onDismiss()
        onSaved(target)
    }

    KeeprSheet(onDismiss = onDismiss) {
        SheetHeader(
            if (person != null) "Edit Person" else "Add Person",
            onCancel = onDismiss,
            confirmTitle = "Save",
            confirmEnabled = canSave,
            onConfirm = ::save,
        )
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            GroupedSection {
                row { FormTextField(givenName, { givenName = it }, "First name", capitalization = KeyboardCapitalization.Words, focusRequester = nameFocus) }
                row { FormTextField(familyName, { familyName = it }, "Last name", capitalization = KeyboardCapitalization.Words) }
                row { FormTextField(preferredName, { preferredName = it }, "Goes by (optional)", capitalization = KeyboardCapitalization.Words) }
            }
            SectionSpacer()
            GroupedSection(header = "Context") {
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
            SectionSpacer()
            GroupedSection(
                header = "Relationship Type",
                footer = "Pick as many as fit. Someone can be a friend and a referral source. Make your own if none of these are how you'd put it.",
            ) {
                relevantTags.forEach { tag ->
                    row {
                        ListRow(onClick = {
                            selectedTagIDs = if (tag.id in selectedTagIDs) selectedTagIDs - tag.id else selectedTagIDs + tag.id
                            haptics.selection()
                        }) {
                            IconLabel(tag.name, tag.symbolName, KeeprTheme.typography.body, KeeprTheme.colors.label, Modifier.weight(1f))
                            if (tag.id in selectedTagIDs) {
                                SfSymbol("checkmark", KeeprTheme.colors.accent, KeeprTheme.typography.body.semibold)
                            }
                        }
                    }
                }
                row {
                    ListRow(onClick = { isShowingTypeEditor = true }) {
                        IconLabel("New Type…", "plus.circle", KeeprTheme.typography.subheadline, KeeprTheme.colors.accent)
                    }
                }
            }
            SectionSpacer()
            GroupedSection(
                header = "Work",
                footer = "\"What they do\" is for your words, not the contact card's — what they run, what they're good at, what you could learn from them.",
            ) {
                row { FormTextField(company, { company = it }, "Company", capitalization = KeyboardCapitalization.Words) }
                row { FormTextField(jobTitle, { jobTitle = it }, "Title", capitalization = KeyboardCapitalization.Words) }
                row { FormTextField(workNote, { workNote = it }, "What they do", maxLines = 4) }
            }
            SectionSpacer()
            GroupedSection(header = "Reach them") {
                row { FormTextField(phone, { phone = it }, "Phone", keyboardType = KeyboardType.Phone) }
                row { FormTextField(email, { email = it }, "Email", keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None) }
            }
            SectionSpacer()
            GroupedSection(header = "Relationship") {
                row { FormToggle("Favorite", isFavorite, { isFavorite = it }) }
                row { FormMenuPicker("Priority", Priority.entries, priority, { it.title }, { priority = it }) }
                row { FormTextField(howWeMet, { howWeMet = it }, "How we met", maxLines = 3) }
                row { FormTextField(introducedBy, { introducedBy = it }, "Introduced by", capitalization = KeyboardCapitalization.Words) }
            }
            SectionSpacer()
            GroupedSection(header = "Notes") {
                row { FormTextField(notes, { notes = it }, "Anything else", minLines = 3, maxLines = 10) }
            }
        }
    }

    // "Make your own": the type editor opens straight into a new type.
    if (isShowingTypeEditor) {
        com.chrisallenclark.keepr.ui.organize.RelationshipTypesSheet(
            onDismiss = { isShowingTypeEditor = false },
            startsCreating = true,
            initialMode = mode,
        )
    }
}

