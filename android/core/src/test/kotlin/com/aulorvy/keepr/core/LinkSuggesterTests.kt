package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.domain.LinkSuggester
import com.aulorvy.keepr.core.model.PersonLink
import com.aulorvy.keepr.core.services.ContactRelation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Translated from KeeprTests/LinkSuggesterTests.swift.

/**
 * Suggested links join two real people on the strength of a name written on a
 * contact card. That's a high-stakes guess, so these tests mostly pin down
 * when it refuses to make one.
 */
class LinkSuggesterTests {

    private fun relation(label: String, name: String): ContactRelation = ContactRelation(label = label, name = name)

    /** A relation naming someone already in Keepr becomes a suggestion */
    @Test
    fun matchingNameSuggestsALink() {
        val store = TestStore()
        val alex = Make.person(store.context, given = "Alex", family = "Nguyen")
        val linda = Make.person(store.context, given = "Linda", family = "Clark")

        val suggestions = LinkSuggester.suggestions(
            relations = listOf(relation("mother", "Linda Clark")),
            person = alex,
            candidates = listOf(alex, linda),
        )

        assertEquals(1, suggestions.size)
        val first = assertNotNull(suggestions.firstOrNull())
        assertEquals(linda.id, first.candidate.id)
        assertEquals("Parent", first.role.name)
        assertTrue(first.reason.contains("mother"))
    }

    /** Case and accents don't stop a match */
    @Test
    fun matchingIsForgivingAboutSpelling() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Test", family = "Person")
        val jose = Make.person(store.context, given = "José", family = "Ruiz")

        val suggestions = LinkSuggester.suggestions(
            relations = listOf(relation("brother", "jose  ruiz")),
            person = person,
            candidates = listOf(person, jose),
        )

        assertEquals(jose.id, suggestions.firstOrNull()?.candidate?.id)
    }

    /** A card that says "Mom" matches the person filed as Mom */
    @Test
    fun preferredNamesCount() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Test", family = "Person")
        val mom = Make.person(
            store.context,
            given = "Linda",
            family = "Clark",
            preferred = "Mom",
        )

        val suggestions = LinkSuggester.suggestions(
            relations = listOf(relation("mother", "Mom")),
            person = person,
            candidates = listOf(person, mom),
        )

        assertEquals(mom.id, suggestions.firstOrNull()?.candidate?.id)
    }

    /** A name nobody in Keepr has is not guessed at */
    @Test
    fun unknownNamesAreSkipped() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Test", family = "Person")
        val similar = Make.person(store.context, given = "Linda", family = "Clarkson")

        val suggestions = LinkSuggester.suggestions(
            relations = listOf(relation("mother", "Linda Clark")),
            person = person,
            candidates = listOf(person, similar),
        )

        assertTrue(suggestions.isEmpty(), "a near-miss is not a match")
    }

    /** Nobody is suggested as a link to themselves */
    @Test
    fun selfIsExcluded() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Linda", family = "Clark")

        val suggestions = LinkSuggester.suggestions(
            relations = listOf(relation("mother", "Linda Clark")),
            person = person,
            candidates = listOf(person),
        )

        assertTrue(suggestions.isEmpty())
    }

    /** An existing link isn't offered a second time */
    @Test
    fun alreadyLinkedIsSkipped() {
        val store = TestStore()
        val alex = Make.person(store.context, given = "Alex", family = "Nguyen")
        val linda = Make.person(store.context, given = "Linda", family = "Clark")
        store.context.insert(
            PersonLink(personA = alex, personB = linda, labelAToB = "Parent", labelBToA = "Child"),
        )
        store.save()

        val suggestions = LinkSuggester.suggestions(
            relations = listOf(relation("mother", "Linda Clark")),
            person = alex,
            candidates = listOf(alex, linda),
        )

        assertTrue(suggestions.isEmpty())
    }

    /** Two relations naming the same person produce one suggestion */
    @Test
    fun candidatesAreNotDoubleCounted() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Test", family = "Person")
        val pat = Make.person(store.context, given = "Pat", family = "Reed")

        val suggestions = LinkSuggester.suggestions(
            relations = listOf(relation("spouse", "Pat Reed"), relation("partner", "Pat Reed")),
            person = person,
            candidates = listOf(person, pat),
        )

        assertEquals(1, suggestions.size)
    }

    /** An empty name on a card matches nobody */
    @Test
    fun blankNamesMatchNothing() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Test", family = "Person")
        val other = Make.person(store.context, given = "Other", family = "Person")

        assertFalse(LinkSuggester.matches(other, name = "   "))
        val suggestions = LinkSuggester.suggestions(
            relations = listOf(relation("mother", "")),
            person = person,
            candidates = listOf(person, other),
        )
        assertTrue(suggestions.isEmpty())
    }
}
