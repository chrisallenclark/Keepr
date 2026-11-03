package com.chrisallenclark.keepr.core.domain

import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import com.chrisallenclark.keepr.core.services.ContactSummary

// Translated from Keepr/Domain/ReviewQueue.swift.

/**
 * One person waiting to be classified.
 *
 * Mixes two things the user experiences as one job: someone whose number they
 * saved on Saturday and never filed, and someone imported months ago who never
 * got a relationship type. Splitting them across two screens would just mean
 * one never gets done.
 */
class ReviewItem(val subject: Subject, val suggestion: CategorySuggestion?) {

    sealed interface Subject {
        /** In the phone's contacts, not yet in Keepr. */
        data class NewContact(val contact: ContactSummary) : Subject
        /** Already in Keepr, with no relationship type on them. */
        data class Existing(val person: Person) : Subject
    }

    val id: String
        get() = when (subject) {
            is Subject.NewContact -> "contact-${subject.contact.identifier}"
            // Swift interpolates a UUID as its uppercase `uuidString`.
            is Subject.Existing -> "person-${subject.person.id.toString().uppercase()}"
        }

    val name: String
        get() = when (subject) {
            is Subject.NewContact -> subject.contact.fullName
            is Subject.Existing -> subject.person.displayName
        }

    val detail: String?
        get() = when (subject) {
            is Subject.NewContact -> subject.contact.subtitle
            is Subject.Existing -> subject.person.subtitle
        }

    val isNew: Boolean get() = subject is Subject.NewContact
}

/**
 * Builds the "needs categorizing" queue. Pure input → output so the rules can
 * be tested without a store, a contact database, or a running app.
 */
object ReviewQueue {

    /**
     * Someone counts as uncategorized when they carry no relationship type at
     * all. Groups don't rescue them: "from the gym" says where you met, not
     * what they are to you.
     */
    fun needsCategorizing(person: Person): Boolean =
        person.status == RelationshipStatus.ACTIVE && person.tagList.isEmpty() && person.reviewedAt == null

    /**
     * New contacts first — the memory is still fresh and about to be lost.
     * Then the backlog, newest first.
     */
    fun items(
        newContacts: List<ContactSummary>,
        people: List<Person>,
        vocabulary: MarkerVocabulary = MarkerVocabulary(),
    ): List<ReviewItem> {
        // Someone already imported is never "new", even if the tracker hasn't
        // caught up — they belong in the backlog or nowhere.
        val linked = people.mapNotNull { it.contactIdentifier }.toSet()

        val fresh = newContacts
            .filter { it.identifier !in linked }
            .map { contact ->
                ReviewItem(
                    ReviewItem.Subject.NewContact(contact),
                    ContactCategorizer.suggestion(contact, vocabulary),
                )
            }

        val backlog = people
            .filter(::needsCategorizing)
            .sortedByDescending { it.createdAt }
            .map { person ->
                ReviewItem(ReviewItem.Subject.Existing(person), ContactCategorizer.suggestion(person))
            }

        return fresh + backlog
    }

    /** One line for a banner: what's waiting, in words rather than a bare count. */
    fun summary(newCount: Int, backlogCount: Int): String? = when {
        newCount == 0 && backlogCount == 0 -> null
        backlogCount == 0 ->
            if (newCount == 1) "1 new contact since you last looked"
            else "$newCount new contacts since you last looked"
        newCount == 0 ->
            if (backlogCount == 1) "1 person has no relationship type yet"
            else "$backlogCount people have no relationship type yet"
        else -> {
            val newText = if (newCount == 1) "1 new contact" else "$newCount new contacts"
            val backlogText = if (backlogCount == 1) "1 more" else "$backlogCount more"
            "$newText, and $backlogText to categorize"
        }
    }
}
