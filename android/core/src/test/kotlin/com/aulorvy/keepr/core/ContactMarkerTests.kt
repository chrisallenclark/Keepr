package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.domain.ContactCategorizer
import com.aulorvy.keepr.core.domain.ContactMarkerParser
import com.aulorvy.keepr.core.domain.MarkerVocabulary
import com.aulorvy.keepr.core.model.PersonGroup
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.model.RelationshipTag
import com.aulorvy.keepr.core.persistence.KeeprStore
import com.aulorvy.keepr.core.services.ContactSummary
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Translated from KeeprTests/ContactMarkerTests.swift.

/**
 * Reading shorthand off a contact card is the highest-leverage thing the
 * importer does and the easiest to get dangerously wrong: it renames people and
 * files them automatically. These tests are mostly about the guardrails.
 */
class ContactMarkerTests {

    private fun contact(
        given: String = "Test",
        family: String = "Person",
        organization: String? = null,
        jobTitle: String? = null,
    ): ContactSummary = ContactSummary(
        identifier = UUID.randomUUID().toString().uppercase(),
        givenName = given,
        familyName = family,
        organizationName = organization,
        jobTitle = jobTitle,
        phoneNumbers = emptyList(),
        emailAddresses = emptyList(),
        thumbnailData = null,
    )

    /** A trainer's setup: three businesses and the built-in types. */
    private fun trainerVocabulary(store: TestStore): MarkerVocabulary {
        KeeprStore.seedIfNeeded(store.context, store.defaults)

        val lifeTime = PersonGroup(name = "Life Time", sortOrder = 0)
        val hyp = PersonGroup(name = "Hybrid Performance", aliases = "HYP", sortOrder = 10)
        val mealPrep = PersonGroup(name = "Meal Prep", sortOrder = 20)
        for (group in listOf(lifeTime, hyp, mealPrep)) store.context.insert(group)
        store.save()

        return MarkerVocabulary.build(
            groups = store.fetch<PersonGroup>(),
            tags = store.fetch<RelationshipTag>(),
        )
    }

    // MARK: - Reading the shorthand

    /** "Stanley LT Client" becomes Stanley, a client, at Life Time */
    @Test
    fun initialsAndTypeAreRead() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val parse = ContactMarkerParser.parse(contact(given = "Stanley", family = "LT Client"), vocabulary)

        assertEquals(listOf("Life Time"), parse.groupNames)
        assertEquals(listOf("Current Client"), parse.typeNames)
        assertEquals("Stanley", parse.givenName)
        assertTrue(parse.familyName.isEmpty(), "the last name field held nothing but markers")
    }

    /** A user-taught alias works — "HYP" means Hybrid Performance */
    @Test
    fun explicitAliasesAreRead() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val parse = ContactMarkerParser.parse(contact(given = "Dana", family = "Whitfield (HYP)"), vocabulary)

        assertEquals(listOf("Hybrid Performance"), parse.groupNames)
        assertEquals("Whitfield", parse.familyName, "the brackets go with the marker")
    }

    /** Initials are understood without being taught */
    @Test
    fun initialsAreDerived() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val parse = ContactMarkerParser.parse(contact(given = "Rob", family = "MP"), vocabulary)

        assertEquals(listOf("Meal Prep"), parse.groupNames)
    }

    /** A full name spelled out matches too, in any case */
    @Test
    fun fullNamesMatchCaseInsensitively() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val parse = ContactMarkerParser.parse(contact(given = "Casey", family = "life time"), vocabulary)

        assertEquals(listOf("Life Time"), parse.groupNames)
    }

    /** Two markers on one card are both read */
    @Test
    fun multipleMarkersAreRead() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val parse = ContactMarkerParser.parse(contact(given = "Sam", family = "LT Meal Prep Client"), vocabulary)

        assertEquals(setOf("Life Time", "Meal Prep"), parse.groupNames.toSet())
        assertEquals(listOf("Current Client"), parse.typeNames)
        assertEquals("Sam", parse.givenName)
    }

    // MARK: - Not damaging real names

    /** A short marker in normal case is left alone — it's probably a name */
    @Test
    fun lowercaseInitialsAreNotMarkers() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val parse = ContactMarkerParser.parse(contact(given = "Lt", family = "Colonel"), vocabulary)

        assertTrue(parse.isEmpty, "\"Lt\" is not shouted, so it isn't Life Time")
        assertEquals("Lt", parse.givenName)
    }

    /** A marker inside a longer word is not a match */
    @Test
    fun markersMatchWholeWordsOnly() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val parse = ContactMarkerParser.parse(contact(given = "Clientele", family = "Salton"), vocabulary)

        assertTrue(parse.isEmpty)
        assertEquals("Clientele", parse.givenName)
    }

    /** A card that is nothing but markers keeps its name */
    @Test
    fun neverLeavesSomeoneNameless() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val parse = ContactMarkerParser.parse(contact(given = "LT", family = "Client"), vocabulary)

        assertEquals(listOf("Life Time"), parse.groupNames)
        assertEquals(listOf("Current Client"), parse.typeNames)
        assertEquals("LT", parse.givenName, "categorized, but not erased")
        assertEquals(false, parse.changedName)
    }

    /** A marker in the company field is evidence, not something to edit out */
    @Test
    fun companyFieldIsNotRewritten() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val parse = ContactMarkerParser.parse(
            contact(given = "Jamie", family = "Boyd", organization = "Life Time"),
            vocabulary,
        )

        assertEquals(listOf("Life Time"), parse.groupNames)
        assertEquals("Boyd", parse.familyName)
        assertEquals(false, parse.changedName, "an employer is a fact, not a label")
    }

    /** With no groups or types set up, nothing is guessed at */
    @Test
    fun emptyVocabularyDoesNothing() {
        val parse = ContactMarkerParser.parse(contact(given = "Stanley", family = "LT Client"), MarkerVocabulary())

        assertTrue(parse.isEmpty)
        assertEquals("LT Client", parse.familyName)
    }

    // MARK: - The whole suggestion

    /** A marker outranks the employer field */
    @Test
    fun markersBeatInferredEmployer() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val summary = contact(given = "Stanley", family = "LT Client", organization = "Stanley Commerce Co.")
        val suggestion = assertNotNull(ContactCategorizer.suggestion(summary, vocabulary))

        assertEquals("Current Client", suggestion.tagName)
        assertEquals(RelationshipContext.BUSINESS, suggestion.context)
        assertEquals(listOf("Life Time"), suggestion.groupNames)
        assertEquals("Stanley", suggestion.cleanedGivenName)
        assertEquals("Stanley LT Client", suggestion.originalName)
    }

    /** A personal type on the card puts them in Personal, not Business */
    @Test
    fun personalTypesSetPersonalContext() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val suggestion = assertNotNull(
            ContactCategorizer.suggestion(contact(given = "Rae", family = "FAMILY"), vocabulary),
        )

        assertEquals(RelationshipContext.PERSONAL, suggestion.context)
        assertEquals("Family", suggestion.tagName)
    }

    /** With no markers, the old employer rules still apply */
    @Test
    fun fallsBackToTheOlderRules() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val suggestion = assertNotNull(
            ContactCategorizer.suggestion(
                contact(given = "Nina", family = "Ortega", organization = "Coastal Design"),
                vocabulary,
            ),
        )

        assertEquals("Professional Contact", suggestion.tagName)
        assertTrue(suggestion.groupNames.isEmpty())
        assertNull(suggestion.cleanedGivenName)
    }

    /** A type the user deleted is not suggested back into existence */
    @Test
    fun deletedTypesAreNotSuggested() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)

        // They don't think of anyone as a "Professional Contact" and got rid of it.
        val unwanted = assertNotNull(
            store.fetch<RelationshipTag>().firstOrNull { it.builtInKey == "Professional Contact" },
        )
        store.context.delete(unwanted)
        store.save()

        val vocabulary = MarkerVocabulary.build(
            groups = store.fetch<PersonGroup>(),
            tags = store.fetch<RelationshipTag>(),
        )
        val suggestion = assertNotNull(
            ContactCategorizer.suggestion(
                contact(given = "Nina", family = "Ortega", organization = "Coastal Design"),
                vocabulary,
            ),
        )

        assertNull(suggestion.tagName, "no resurrecting a type they threw away")
        assertEquals(RelationshipContext.BUSINESS, suggestion.context, "the employer still says which side they're on")
    }

    // MARK: - Discovery

    /** Shorthand on several cards is offered as a group to create */
    @Test
    fun repeatedShorthandIsDiscovered() {
        val contacts = listOf(
            contact(given = "Stanley", family = "LT Client"),
            contact(given = "Rae", family = "LT"),
            contact(given = "Nina", family = "Ortega (HYP)"),
            contact(given = "Sam", family = "Smith"),
        )

        val found = ContactMarkerParser.candidates(contacts, MarkerVocabulary())

        assertEquals("LT", found.firstOrNull()?.token)
        assertEquals(2, found.firstOrNull()?.count)
        assertEquals(false, found.any { it.token == "HYP" }, "one card isn't a habit")
    }

    /** Shorthand Keepr already understands isn't offered again */
    @Test
    fun knownShorthandIsNotOffered() {
        val store = TestStore()
        val vocabulary = trainerVocabulary(store)

        val found = ContactMarkerParser.candidates(
            listOf(
                contact(given = "Stanley", family = "LT Client"),
                contact(given = "Rae", family = "LT"),
            ),
            vocabulary,
        )

        assertTrue(found.isEmpty())
    }

    /** Ordinary names are not mistaken for shorthand */
    @Test
    fun plainNamesAreNotCandidates() {
        val found = ContactMarkerParser.candidates(
            listOf(
                contact(given = "Sarah", family = "Miller"),
                contact(given = "Sarah", family = "Miller"),
                contact(given = "Michael", family = "Reed"),
            ),
            MarkerVocabulary(),
        )

        assertTrue(found.isEmpty())
    }

    // MARK: - Vocabulary

    /** A group answers to its name, its initials, and anything you add */
    @Test
    fun vocabularyTermsCoverTheObviousSpellings() {
        val terms = MarkerVocabulary.terms("Life Time", aliases = "LTF, Delray Gym").toSet()

        assertTrue(terms.contains("life time"))
        assertTrue(terms.contains("lifetime"))
        assertTrue(terms.contains("lt"))
        assertTrue(terms.contains("ltf"))
        assertTrue(terms.contains("delray gym"))
    }

    /** Longer terms are tried first, so "Current Client" beats "Client" */
    @Test
    fun longerTermsSortFirst() {
        val terms = MarkerVocabulary.terms("Current Client", aliases = null, extra = listOf("client"))
        assertEquals("current client", terms.firstOrNull())
    }
}
