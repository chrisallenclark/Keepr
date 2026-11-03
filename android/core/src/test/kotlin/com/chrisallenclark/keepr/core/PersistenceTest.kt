package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Interaction
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.MemoryCategory
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.model.TagKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Translated from KeeprTests/PersistenceTests.swift. These exercise the
 * in-memory context, as the iOS suite does; the Room round trip (writing to
 * disk and reading back) is covered by the app's instrumented tests.
 */
class PersistenceTest {

    // Round trip

    @Test
    fun graphRoundTrips() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Ada", family = "Lovelace", relationship = RelationshipContext.BUSINESS, company = "Analytical Engines")
        val tag = Make.tag(store.context, name = "Investor", kind = TagKind.BUSINESS)
        Make.attach(tag, person)
        val interaction = Make.interaction(store.context, kind = InteractionKind.MEETING, occurredAt = TestDates.days(-2), summary = "Coffee at the shop", person = person)
        val memory = Make.memory(store.context, content = "Prefers early meetings", category = MemoryCategory.PREFERENCES, person = person, sourceInteraction = interaction)
        val followUp = Make.followUp(store.context, title = "Send the deck", due = TestDates.dayStart(3), person = person, sourceInteraction = interaction)
        store.save()

        val people = store.fetch<Person>()
        assertEquals(1, people.size)
        val fetched = assertNotNull(people.firstOrNull())
        assertEquals("Ada Lovelace", fetched.fullName)
        assertEquals(listOf("Investor"), fetched.tagList.map { it.name })
        assertEquals(listOf(interaction.id), fetched.interactionList.map { it.id })
        assertEquals(listOf(memory.id), fetched.memoryList.map { it.id })
        assertEquals(listOf(followUp.id), fetched.followUpList.map { it.id })
        assertEquals(interaction.id, fetched.memoryList.firstOrNull()?.sourceInteraction?.id)
        assertEquals(interaction.id, fetched.followUpList.firstOrNull()?.sourceInteraction?.id)
        assertEquals(RelationshipContext.BUSINESS, fetched.context)
        assertEquals("Analytical Engines", fetched.company)
    }

    // Cascades

    @Test
    fun deletingPersonCascades() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Ada", family = "Lovelace")
        val interaction = Make.interaction(store.context, summary = "Coffee", person = person)
        Make.memory(store.context, content = "Likes coffee", person = person, sourceInteraction = interaction)
        Make.followUp(store.context, due = TestDates.dayStart(1), person = person)
        store.save()

        store.context.delete(person)
        store.save()

        assertTrue(store.fetch<Person>().isEmpty())
        assertTrue(store.fetch<Interaction>().isEmpty())
        assertTrue(store.fetch<Memory>().isEmpty())
        assertTrue(store.fetch<FollowUp>().isEmpty())
    }

    @Test
    fun deletingPersonKeepsTags() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Ada", family = "Lovelace")
        val tag = Make.tag(store.context, name = "Investor", kind = TagKind.BUSINESS)
        Make.attach(tag, person)
        store.save()

        store.context.delete(person)
        store.save()

        val tags = store.fetch<RelationshipTag>()
        assertEquals(listOf("Investor"), tags.map { it.name })
        assertEquals(true, tags.firstOrNull()?.peopleList?.isEmpty())
    }

    @Test
    fun deletingInteractionNullifiesSources() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Ada", family = "Lovelace")
        val interaction = Make.interaction(store.context, summary = "Coffee", person = person)
        val memory = Make.memory(store.context, content = "Prefers early meetings", person = person, sourceInteraction = interaction)
        val followUp = Make.followUp(store.context, title = "Send the deck", due = TestDates.dayStart(3), person = person, sourceInteraction = interaction)
        store.save()

        store.context.delete(interaction)
        store.save()

        assertTrue(store.fetch<Interaction>().isEmpty())

        val memories = store.fetch<Memory>()
        assertEquals(listOf(memory.id), memories.map { it.id })
        val survivingMemory = assertNotNull(memories.firstOrNull())
        assertNull(survivingMemory.sourceInteraction)
        assertEquals(person.id, survivingMemory.person?.id)

        val followUps = store.fetch<FollowUp>()
        assertEquals(listOf(followUp.id), followUps.map { it.id })
        val survivingFollowUp = assertNotNull(followUps.firstOrNull())
        assertNull(survivingFollowUp.sourceInteraction)
        assertEquals(person.id, survivingFollowUp.person?.id)
    }

    // Contact linking

    @Test
    fun contactLinkingFlag() {
        val store = TestStore()
        val linked = Make.person(store.context, given = "Lin", family = "Linked", contactIdentifier = "ABC-123")
        val unlinked = Make.person(store.context, given = "Una", family = "Unlinked")
        store.save()

        assertTrue(linked.isLinkedToContact)
        assertFalse(unlinked.isLinkedToContact)
        assertEquals("ABC-123", linked.contactIdentifier)
        assertNull(unlinked.contactIdentifier)
    }

    @Test
    fun distinctContactIdentifiers() {
        val store = TestStore()
        Make.person(store.context, given = "One", family = "Alpha", contactIdentifier = "ID-1")
        Make.person(store.context, given = "Two", family = "Bravo", contactIdentifier = "ID-2")
        store.save()

        val identifiers = store.fetch<Person>().mapNotNull { it.contactIdentifier }.sorted()
        assertEquals(listOf("ID-1", "ID-2"), identifiers)
    }

    @Test
    fun duplicateContactIdentifiersAreAllowed() {
        val store = TestStore()
        val first = Make.person(store.context, given = "One", family = "Alpha", contactIdentifier = "ID-1")
        val second = Make.person(store.context, given = "Two", family = "Bravo")
        // No unique constraint anywhere in the schema — sync forbids it.
        second.contactIdentifier = first.contactIdentifier
        store.save()

        val duplicates = store.fetch<Person>().filter { it.contactIdentifier == "ID-1" }
        assertEquals(2, duplicates.size)
    }

    // Shared tags

    @Test
    fun sharedTagReportsBothPeople() {
        val store = TestStore()
        val tag = Make.tag(store.context, name = "Close Friend", kind = TagKind.PERSONAL)
        val first = Make.person(store.context, given = "Ann", family = "Adams")
        val second = Make.person(store.context, given = "Bob", family = "Baker")
        Make.attach(tag, first)
        Make.attach(tag, second)
        store.save()

        val tags = store.fetch<RelationshipTag>()
        assertEquals(1, tags.size)
        val fetched = assertNotNull(tags.firstOrNull())
        assertEquals(setOf(first.id, second.id), fetched.peopleList.map { it.id }.toSet())
        assertTrue(first.hasTag("Close Friend"))
        assertTrue(second.hasTag("Close Friend"))
    }

    @Test
    fun deletingTagKeepsPeople() {
        val store = TestStore()
        val tag = Make.tag(store.context, name = "Lead", kind = TagKind.BUSINESS)
        val first = Make.person(store.context, given = "Ann", family = "Adams")
        val second = Make.person(store.context, given = "Bob", family = "Baker")
        Make.attach(tag, first)
        Make.attach(tag, second)
        store.save()

        store.context.delete(tag)
        store.save()

        assertTrue(store.fetch<RelationshipTag>().isEmpty())
        assertEquals(2, store.fetch<Person>().size)
        assertTrue(first.tagList.isEmpty())
        assertTrue(second.tagList.isEmpty())
    }
}
