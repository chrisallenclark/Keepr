package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.ReviewQueue
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import com.chrisallenclark.keepr.core.model.TagKind
import com.chrisallenclark.keepr.core.services.ContactSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Translated from KeeprTests/ReviewQueueTests.swift.

/**
 * The catch-up queue is the one screen that tells the user they have work to
 * do, so it has to be right about who's actually waiting — and silent about
 * everyone else.
 */
class ReviewQueueTests {

    private fun contact(
        identifier: String,
        given: String = "New",
        family: String = "Contact",
        organization: String? = null,
    ): ContactSummary = ContactSummary(
        identifier = identifier,
        givenName = given,
        familyName = family,
        organizationName = organization,
        jobTitle = null,
        phoneNumbers = emptyList(),
        emailAddresses = emptyList(),
        thumbnailData = null,
    )

    // MARK: - Who needs categorizing

    /** Someone with no relationship type is waiting */
    @Test
    fun untaggedPeopleAreQueued() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Un", family = "Categorized")

        assertTrue(ReviewQueue.needsCategorizing(person))
    }

    /** A relationship type takes them out of the queue */
    @Test
    fun taggedPeopleAreDone() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Sorted", family = "Person")
        Make.attach(Make.tag(store.context, name = "Current Client"), to = person)

        assertFalse(ReviewQueue.needsCategorizing(person))
    }

    /** Archived people are not chased */
    @Test
    fun archivedPeopleAreLeftAlone() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Old", family = "Contact", status = RelationshipStatus.ARCHIVED)

        assertFalse(ReviewQueue.needsCategorizing(person))
    }

    /** "Not now" sticks — a waved-off person isn't asked about again */
    @Test
    fun skippedPeopleStaySkipped() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Later", family = "Person")
        assertTrue(ReviewQueue.needsCategorizing(person))

        person.reviewedAt = TestDates.now

        assertFalse(ReviewQueue.needsCategorizing(person))
    }

    // MARK: - Building the queue

    /** New contacts come before the backlog, because that memory is fading */
    @Test
    fun newContactsSortFirst() {
        val store = TestStore()
        val existing = Make.person(store.context, given = "Old", family = "Person")

        val items = ReviewQueue.items(newContacts = listOf(contact("new-1")), people = listOf(existing))

        assertEquals(2, items.size)
        assertEquals(true, items.firstOrNull()?.isNew)
        assertEquals(false, items.lastOrNull()?.isNew)
    }

    /** A contact already imported isn't offered again as new */
    @Test
    fun importedContactsAreNotNew() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Already", family = "Here", contactIdentifier = "new-1")
        Make.attach(Make.tag(store.context, name = "Vendor"), to = person)

        val items = ReviewQueue.items(newContacts = listOf(contact("new-1")), people = listOf(person))

        assertTrue(items.isEmpty())
    }

    /** An imported but uncategorized contact appears once, not twice */
    @Test
    fun importedAndUncategorizedAppearsOnce() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Already", family = "Here", contactIdentifier = "new-1")

        val items = ReviewQueue.items(newContacts = listOf(contact("new-1")), people = listOf(person))

        assertEquals(1, items.size)
        assertEquals(false, items.firstOrNull()?.isNew)
    }

    /** The backlog is newest first — recent additions are the ones you can still place */
    @Test
    fun backlogIsNewestFirst() {
        val store = TestStore()
        val older = Make.person(store.context, given = "Older", family = "Person", createdAt = TestDates.days(-30))
        val newer = Make.person(store.context, given = "Newer", family = "Person", createdAt = TestDates.days(-1))

        val items = ReviewQueue.items(newContacts = emptyList(), people = listOf(older, newer))

        assertEquals(listOf("Newer Person", "Older Person"), items.map { it.name })
    }

    /** Suggestions ride along with each item, for people and contacts alike */
    @Test
    fun itemsCarrySuggestions() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Dana", family = "Whitfield", company = "Whitfield Interiors")

        val items = ReviewQueue.items(
            newContacts = listOf(contact("new-1", organization = "Martinez Roofing")),
            people = listOf(person),
        )

        assertEquals(2, items.size)
        assertTrue(items.all { it.suggestion != null })
        assertEquals(RelationshipContext.BUSINESS, items.firstOrNull()?.suggestion?.context)
    }

    /** Nothing to do produces an empty queue rather than an empty section */
    @Test
    fun nothingWaitingIsEmpty() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Sorted", family = "Person")
        Make.attach(Make.tag(store.context, name = "Friend", kind = TagKind.PERSONAL), to = person)

        assertTrue(ReviewQueue.items(newContacts = emptyList(), people = listOf(person)).isEmpty())
    }

    // MARK: - Summary line

    /** The banner says nothing when there's nothing waiting */
    @Test
    fun summaryIsSilentWhenEmpty() {
        assertNull(ReviewQueue.summary(newCount = 0, backlogCount = 0))
    }

    /** Counts are spelled out in words, singular and plural */
    @Test
    fun summaryReadsProperly() {
        val one = assertNotNull(ReviewQueue.summary(newCount = 1, backlogCount = 0))
        assertEquals("1 new contact since you last looked", one)

        val many = assertNotNull(ReviewQueue.summary(newCount = 5, backlogCount = 0))
        assertTrue(many.startsWith("5 new contacts"))

        val backlog = assertNotNull(ReviewQueue.summary(newCount = 0, backlogCount = 1))
        assertEquals("1 person has no relationship type yet", backlog)

        val both = assertNotNull(ReviewQueue.summary(newCount = 2, backlogCount = 7))
        assertEquals("2 new contacts, and 7 more to categorize", both)
    }
}
