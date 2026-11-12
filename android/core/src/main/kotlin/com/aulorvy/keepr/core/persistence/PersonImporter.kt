package com.aulorvy.keepr.core.persistence

import com.aulorvy.keepr.core.domain.CategorySuggestion
import com.aulorvy.keepr.core.model.KeeprCalendar
import com.aulorvy.keepr.core.model.Memory
import com.aulorvy.keepr.core.model.MemoryCategory
import com.aulorvy.keepr.core.model.ModelContext
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonGroup
import com.aulorvy.keepr.core.model.Priority
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.model.TagKind
import com.aulorvy.keepr.core.model.capitalizedWords
import com.aulorvy.keepr.core.services.ContactSummary

/**
 * Turning a phone contact into a Keepr person, in one place.
 * Translated from Keepr/Persistence/PersonImporter.swift.
 *
 * Two screens do this — the contact importer and the review queue — and they
 * must agree exactly: same fields copied, same memories created, same
 * suggestion applied. Two near-identical copies is how one of them quietly
 * stops copying birthdays.
 */
object PersonImporter {

    /** Creates the person and copies across everything the card already knows. */
    fun makePerson(
        contact: ContactSummary,
        relationship: RelationshipContext,
        modelContext: ModelContext,
        suggestion: CategorySuggestion? = null,
    ): Person {
        val person = Person(
            givenName = suggestion?.cleanedGivenName ?: contact.givenName,
            familyName = suggestion?.cleanedFamilyName ?: contact.familyName,
            context = relationship,
            contactIdentifier = contact.identifier,
            company = contact.organizationName,
            jobTitle = contact.jobTitle,
            phoneNumbers = contact.phoneNumbers,
            emailAddresses = contact.emailAddresses,
            photoData = contact.thumbnailData,
        )
        modelContext.insert(person)

        person.birthday = contact.birthday
        person.postalAddress = contact.postalAddress
        person.contactNote = contact.note

        return person
    }

    /**
     * Facts already on the contact card become memories, so a profile isn't
     * empty the moment someone is imported. Only things the user typed
     * themselves — nothing inferred. [calendar] formats the birthday
     * ("June 15"), as iOS's current calendar and locale do.
     */
    fun addMemories(
        contact: ContactSummary,
        person: Person,
        modelContext: ModelContext,
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ) {
        contact.birthday?.let { birthday ->
            val formatted = calendar.fullMonthDay(birthday)
            modelContext.insert(
                Memory(
                    content = "Birthday: $formatted",
                    category = MemoryCategory.IMPORTANT_DATE,
                    importance = Priority.HIGH,
                    person = person,
                ),
            )
        }

        for (relation in contact.relations) {
            modelContext.insert(
                Memory(
                    content = "${relation.label.capitalizedWords()}: ${relation.name}",
                    category = MemoryCategory.FAMILY,
                    person = person,
                ),
            )
        }

        val note = contact.note
        if (note != null && note.isNotEmpty()) {
            modelContext.insert(Memory(content = note, category = MemoryCategory.OTHER, person = person))
        }
    }

    /**
     * Applies a suggested category. Adds types and groups rather than
     * replacing what's there — a suggestion never removes a decision the user made.
     */
    fun apply(
        suggestion: CategorySuggestion,
        person: Person,
        modelContext: ModelContext,
        groups: List<PersonGroup> = emptyList(),
    ) {
        person.context = suggestion.context

        val kind = if (suggestion.context == RelationshipContext.PERSONAL) TagKind.PERSONAL else TagKind.BUSINESS
        for (name in listOfNotNull(suggestion.tagName) + suggestion.extraTagNames) {
            val tag = KeeprStore.tag(name, kind, modelContext)
            if (person.tagList.any { it.id == tag.id }) continue
            person.tags = person.tagList + tag
        }

        for (id in suggestion.groupIDs) {
            val group = groups.firstOrNull { it.id.toString().uppercase() == id } ?: continue
            if (person.isMember(group)) continue
            person.groups = person.groupList + group
        }
    }

    /**
     * Records what the contact card actually said, when shorthand was removed
     * from the name. The cleanup is only safe because it's reversible by
     * reading: the original goes on the profile, and the phone's card is never touched.
     */
    fun noteOriginalName(suggestion: CategorySuggestion, person: Person, modelContext: ModelContext) {
        val original = suggestion.originalName ?: return
        modelContext.insert(
            Memory(
                content = "Contact card says: $original",
                category = MemoryCategory.OTHER,
                importance = Priority.LOW,
                person = person,
            ),
        )
    }
}
