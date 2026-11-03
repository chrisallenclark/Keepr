package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.services.ContactChangeTracker
import com.chrisallenclark.keepr.core.services.ContactSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Translated from KeeprTests/ContactChangeTrackerTests.swift. The Swift file's
// private `MemoryStore` (an in-memory `KeyValueStoring`) is [InMemoryPreferences]
// from TestSupport, so these tests never write to the machine running them.

/**
 * Noticing new contacts is only useful if it stays quiet the rest of the time.
 * These tests are mostly about the not-nagging half.
 */
class ContactChangeTrackerTests {

    private fun contact(identifier: String): ContactSummary = ContactSummary(
        identifier = identifier,
        givenName = "Test",
        familyName = identifier,
        organizationName = null,
        jobTitle = null,
        phoneNumbers = emptyList(),
        emailAddresses = emptyList(),
        thumbnailData = null,
    )

    private fun contacts(vararg identifiers: String): List<ContactSummary> = identifiers.map(::contact)

    /** The first look reports nothing — an address book is not a to-do list */
    @Test
    fun firstRunEstablishesABaseline() {
        val tracker = ContactChangeTracker(InMemoryPreferences())

        val result = tracker.newContacts(contacts("a", "b", "c"))

        assertTrue(result.isEmpty())
        assertTrue(tracker.hasBaseline)
    }

    /** Contacts added after the baseline are reported */
    @Test
    fun laterAdditionsAreNoticed() {
        val tracker = ContactChangeTracker(InMemoryPreferences())
        tracker.newContacts(contacts("a", "b"))

        val result = tracker.newContacts(contacts("a", "b", "c", "d"))

        assertEquals(listOf("c", "d"), result.map { it.identifier })
    }

    /** The same new contact is reported until it's answered for, then never again */
    @Test
    fun acknowledgingStopsTheNagging() {
        val tracker = ContactChangeTracker(InMemoryPreferences())
        tracker.newContacts(contacts("a"))

        assertEquals(1, tracker.newContacts(contacts("a", "b")).size)
        assertEquals(1, tracker.newContacts(contacts("a", "b")).size, "unanswered stays open")

        tracker.acknowledge(contacts("b"))

        assertTrue(tracker.newContacts(contacts("a", "b")).isEmpty())
    }

    /** State survives a relaunch, because it lives in the store not in memory */
    @Test
    fun stateIsPersisted() {
        val store = InMemoryPreferences()
        val first = ContactChangeTracker(store)
        first.newContacts(contacts("a"))

        val second = ContactChangeTracker(store)

        assertTrue(second.hasBaseline)
        assertEquals(listOf("b"), second.newContacts(contacts("a", "b")).map { it.identifier })
    }

    /** A contact deleted and re-added counts as new again */
    @Test
    fun deletedContactsAreForgotten() {
        val tracker = ContactChangeTracker(InMemoryPreferences())
        tracker.newContacts(contacts("a", "b"))

        // "b" leaves the address book...
        assertTrue(tracker.newContacts(contacts("a")).isEmpty())
        // ...and comes back.
        assertEquals(listOf("b"), tracker.newContacts(contacts("a", "b")).map { it.identifier })
    }

    /** Erasing everything re-baselines rather than leaving stale identifiers */
    @Test
    fun resetClearsTheBaseline() {
        val tracker = ContactChangeTracker(InMemoryPreferences())
        tracker.newContacts(contacts("a"))

        tracker.reset()

        assertFalse(tracker.hasBaseline)
        assertTrue(tracker.newContacts(contacts("a", "b")).isEmpty(), "the next look re-baselines")
        assertEquals(listOf("c"), tracker.newContacts(contacts("a", "b", "c")).map { it.identifier })
    }

    /** Acknowledging nothing is not an error */
    @Test
    fun acknowledgingAnEmptyListIsHarmless() {
        val tracker = ContactChangeTracker(InMemoryPreferences())
        tracker.newContacts(contacts("a"))

        tracker.acknowledge(emptyList<String>())

        assertEquals(listOf("b"), tracker.newContacts(contacts("a", "b")).map { it.identifier })
    }
}
