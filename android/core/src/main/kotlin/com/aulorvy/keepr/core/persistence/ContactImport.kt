package com.aulorvy.keepr.core.persistence

import com.aulorvy.keepr.core.domain.CategorySuggestion
import com.aulorvy.keepr.core.domain.ContactCategorizer
import com.aulorvy.keepr.core.domain.MarkerVocabulary
import com.aulorvy.keepr.core.domain.ReviewItem
import com.aulorvy.keepr.core.model.ContextMode
import com.aulorvy.keepr.core.model.Interaction
import com.aulorvy.keepr.core.model.InteractionKind
import com.aulorvy.keepr.core.model.KeeprCalendar
import com.aulorvy.keepr.core.model.Memory
import com.aulorvy.keepr.core.model.ModelContext
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonGroup
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.model.RelationshipTag
import com.aulorvy.keepr.core.model.foldedContains
import com.aulorvy.keepr.core.services.ContactChangeTracker
import com.aulorvy.keepr.core.services.ContactSummary
import java.time.Instant

/**
 * The decisions inside Keepr/Features/People/ContactImportView.swift, without
 * the view: what the search shows, what "Select All" picks, how a chosen
 * contact becomes a person. Kept here so the Android sheet and its tests run
 * exactly the same rules.
 *
 * Duplicates are judged the way iOS judges them — by the contact link only.
 * A contact whose identifier is already on someone in Keepr shows as "Added"
 * and can't be picked again. Someone typed in by hand with the same name or
 * number is *not* treated as the same person: iOS doesn't guess at that,
 * because two Mike Smiths are common and merging the wrong ones loses data.
 */
object ContactImport {

    /** Identifiers already linked to someone in Keepr. */
    fun linkedIdentifiers(people: List<Person>): Set<String> =
        people.filter { !it.isDeleted }.mapNotNull { it.contactIdentifier }.toSet()

    /** `results`: the search, matching name or company, case- and accent-insensitively. */
    fun results(contacts: List<ContactSummary>, query: String): List<ContactSummary> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return contacts
        return contacts.filter {
            it.fullName.foldedContains(trimmed) || (it.organizationName ?: "").foldedContains(trimmed)
        }
    }

    /** Every suggestion for these contacts against the user's vocabulary. */
    fun suggestions(contacts: List<ContactSummary>, vocabulary: MarkerVocabulary): Map<String, CategorySuggestion> {
        val result = mutableMapOf<String, CategorySuggestion>()
        for (contact in contacts) {
            ContactCategorizer.suggestion(contact, vocabulary)?.let { result[contact.identifier] = it }
        }
        return result
    }

    /** "Select All": everything visible that isn't already in Keepr. */
    fun selectAll(results: List<ContactSummary>, linked: Set<String>): Set<String> =
        results.filter { it.identifier !in linked }.map { it.identifier }.toSet()

    /** "Select N with Suggestions". */
    fun importableWithSuggestions(
        results: List<ContactSummary>,
        suggestions: Map<String, CategorySuggestion>,
        linked: Set<String>,
    ): List<ContactSummary> = results.filter { suggestions[it.identifier] != null && it.identifier !in linked }

    /** The name a row shows: cleaned of shorthand when suggestions are on and something was removed. */
    fun displayName(contact: ContactSummary, suggestion: CategorySuggestion?, applySuggestions: Boolean): String {
        if (!applySuggestions || suggestion == null || suggestion.originalName == null) return contact.fullName
        val cleaned = listOfNotNull(suggestion.cleanedGivenName, suggestion.cleanedFamilyName)
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return cleaned.ifEmpty { contact.fullName }
    }

    /** "Professional Contact · Life Time" — the type(s) and groups a suggestion would apply. */
    fun suggestionLabel(suggestion: CategorySuggestion): String =
        (listOfNotNull(suggestion.tagName) + suggestion.extraTagNames + suggestion.groupNames).joinToString(" · ")

    /**
     * `importSelected()`: one person per chosen contact, in the contacts'
     * order. Contacts already linked are skipped, so a second import can never
     * duplicate anyone. The caller saves and acknowledges with the tracker.
     */
    fun importContacts(
        chosen: List<ContactSummary>,
        suggestions: Map<String, CategorySuggestion>,
        applySuggestions: Boolean,
        mode: ContextMode,
        modelContext: ModelContext,
        groups: List<PersonGroup>,
        calendar: KeeprCalendar = KeeprCalendar.current(),
        now: Instant = Instant.now(),
    ): List<Person> {
        val linked = linkedIdentifiers(modelContext.fetch()).toMutableSet()
        val created = mutableListOf<Person>()
        for (contact in chosen) {
            if (!linked.add(contact.identifier)) continue
            val suggestion = if (applySuggestions) suggestions[contact.identifier] else null
            val person = PersonImporter.makePerson(
                contact,
                suggestion?.context ?: RelationshipContext.of(mode),
                modelContext,
                suggestion,
            )
            person.createdAt = now
            person.updatedAt = now
            if (suggestion != null) {
                PersonImporter.apply(suggestion, person, modelContext, groups)
                PersonImporter.noteOriginalName(suggestion, person, modelContext)
            }
            PersonImporter.addMemories(contact, person, modelContext, calendar)
            created += person
        }
        return created
    }
}

/**
 * The two swipe actions of Keepr/Features/Review/ReviewView.swift. The caller
 * saves; both are safe to call for an item that's already been handled.
 */
object ReviewActions {

    /**
     * "Not Now". Waving someone off has to stick, or the queue becomes a nag:
     * a contact's identifier is remembered as seen; someone already in Keepr
     * gets a date on their record.
     */
    fun skip(item: ReviewItem, tracker: ContactChangeTracker, now: Instant = Instant.now()) {
        when (val subject = item.subject) {
            is ReviewItem.Subject.NewContact -> tracker.acknowledge(listOf(subject.contact.identifier))
            is ReviewItem.Subject.Existing -> subject.person.reviewedAt = now
        }
    }

    /** "Accept": the suggestion, applied — importing the contact first when it's new. Returns the person. */
    fun accept(
        item: ReviewItem,
        suggestion: CategorySuggestion,
        modelContext: ModelContext,
        groups: List<PersonGroup>,
        tracker: ContactChangeTracker,
        calendar: KeeprCalendar = KeeprCalendar.current(),
        now: Instant = Instant.now(),
    ): Person = when (val subject = item.subject) {
        is ReviewItem.Subject.NewContact -> {
            val contact = subject.contact
            // Imported elsewhere in the meantime (Catch Up and the importer can
            // both be open on a phone): categorize that person, don't add a twin.
            val existing = modelContext.fetch<Person>().firstOrNull { it.contactIdentifier == contact.identifier }
            val person = existing ?: PersonImporter.makePerson(contact, suggestion.context, modelContext, suggestion).also {
                it.createdAt = now
                it.updatedAt = now
            }
            PersonImporter.apply(suggestion, person, modelContext, groups)
            if (existing == null) {
                PersonImporter.noteOriginalName(suggestion, person, modelContext)
                PersonImporter.addMemories(contact, person, modelContext, calendar)
            }
            tracker.acknowledge(listOf(contact.identifier))
            person
        }
        is ReviewItem.Subject.Existing -> {
            PersonImporter.apply(suggestion, subject.person, modelContext, groups)
            subject.person.touch(now)
            subject.person
        }
    }

    /**
     * `CategorizeSheet.save()`: place one person — imported first when it's
     * a new contact — with the type, groups and story the user just gave.
     * Answering counts as reviewing them, even if no type was chosen.
     */
    fun categorize(
        item: ReviewItem,
        answer: CategorizeAnswer,
        modelContext: ModelContext,
        tracker: ContactChangeTracker,
        calendar: KeeprCalendar = KeeprCalendar.current(),
        now: Instant = Instant.now(),
    ): Person {
        val person = when (val subject = item.subject) {
            is ReviewItem.Subject.NewContact -> {
                val contact = subject.contact
                val existing = modelContext.fetch<Person>().firstOrNull { it.contactIdentifier == contact.identifier }
                val person = existing ?: PersonImporter.makePerson(contact, answer.context, modelContext, item.suggestion).also { created ->
                    created.createdAt = now
                    created.updatedAt = now
                    item.suggestion?.let { PersonImporter.noteOriginalName(it, created, modelContext) }
                    PersonImporter.addMemories(contact, created, modelContext, calendar)
                }
                person.context = answer.context
                tracker.acknowledge(listOf(contact.identifier))
                person
            }
            is ReviewItem.Subject.Existing -> subject.person.also { it.context = answer.context }
        }

        answer.tag?.let { tag ->
            if (person.tagList.none { it.id == tag.id }) person.tags = person.tagList + tag
        }
        person.groups = answer.groups
        person.howWeMet = answer.howWeMet.trim().ifEmpty { null }

        val memory = answer.memory.trim()
        if (memory.isNotEmpty()) modelContext.insert(Memory(content = memory, createdAt = now, person = person))

        answer.interactionKind?.let { kind ->
            val note = answer.interactionNote.trim()
            modelContext.insert(
                Interaction(kind = kind, occurredAt = now, summary = note.ifEmpty { null }, person = person),
            )
            person.lastInteractionAt = now
        }

        person.reviewedAt = now
        person.touch(now)
        return person
    }
}

/** What the user answered on the categorize screen. */
data class CategorizeAnswer(
    val context: RelationshipContext,
    val tag: RelationshipTag? = null,
    val groups: List<PersonGroup> = emptyList(),
    val howWeMet: String = "",
    val memory: String = "",
    /** Null when "Log this as an interaction" is off. */
    val interactionKind: InteractionKind? = null,
    val interactionNote: String = "",
)
