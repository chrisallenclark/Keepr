package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.CategorySuggestion
import com.chrisallenclark.keepr.core.domain.ContactCategorizer
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.services.ContactRelation
import com.chrisallenclark.keepr.core.services.ContactSummary
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Translated from KeeprTests/ContactCategorizerTests.swift.

/**
 * Auto-categorization decides where imported people land, and a wrong guess
 * that arrives pre-selected gets accepted in bulk. These tests pin the rules
 * that make it guess — and, more importantly, the ones that make it decline.
 */
class ContactCategorizerTests {

    private fun contact(
        given: String = "Test",
        family: String = "Person",
        organization: String? = null,
        jobTitle: String? = null,
        emails: List<String> = emptyList(),
        relations: List<ContactRelation> = emptyList(),
    ): ContactSummary = ContactSummary(
        identifier = UUID.randomUUID().toString().uppercase(),
        givenName = given,
        familyName = family,
        organizationName = organization,
        jobTitle = jobTitle,
        phoneNumbers = emptyList(),
        emailAddresses = emails,
        thumbnailData = null,
        relations = relations,
    )

    // MARK: - Declining to guess

    /** A bare name and phone number produces no suggestion at all */
    @Test
    fun noSignalMeansNoGuess() {
        assertNull(ContactCategorizer.suggestion(contact()))
    }

    /** A consumer email address is not evidence of anything */
    @Test
    fun consumerEmailIsNotASignal() {
        for (provider in listOf("gmail.com", "icloud.com", "yahoo.com", "outlook.com", "proton.me")) {
            val summary = contact(emails = listOf("someone@$provider"))
            assertNull(
                ContactCategorizer.suggestion(summary),
                "$provider should not imply a business relationship",
            )
        }
    }

    // MARK: - Business

    /** An employer makes it business */
    @Test
    fun employerImpliesBusiness() {
        val summary = contact(organization = "Coastal Design Co.")
        val suggestion = assertNotNull(ContactCategorizer.suggestion(summary))

        assertEquals(RelationshipContext.BUSINESS, suggestion.context)
        assertEquals("Professional Contact", suggestion.tagName)
        assertEquals("Works at Coastal Design Co.", suggestion.reason)
        assertEquals(CategorySuggestion.Confidence.HIGH, suggestion.confidence)
    }

    /** An owner-shaped job title suggests a potential client, not a colleague */
    @Test
    fun ownerTitlesSuggestALead() {
        for (title in listOf("Owner", "Founder", "CEO", "President", "Managing Partner")) {
            val summary = contact(organization = "Martinez Roofing", jobTitle = title)
            val suggestion = assertNotNull(ContactCategorizer.suggestion(summary))
            assertEquals("Potential Client", suggestion.tagName, "$title should read as a lead")
        }
    }

    /** A regular job title stays a professional contact */
    @Test
    fun regularTitlesStayProfessional() {
        val summary = contact(organization = "Reed PT", jobTitle = "Physical Therapist")
        val suggestion = assertNotNull(ContactCategorizer.suggestion(summary))

        assertEquals("Professional Contact", suggestion.tagName)
        assertEquals("Physical Therapist at Reed PT", suggestion.reason)
    }

    /** A work email domain implies an employer the card didn't name */
    @Test
    fun workEmailImpliesBusiness() {
        val summary = contact(emails = listOf("jake@martinezroofing.com"))
        val suggestion = assertNotNull(ContactCategorizer.suggestion(summary))

        assertEquals(RelationshipContext.BUSINESS, suggestion.context)
        assertEquals("Work email at martinezroofing.com", suggestion.reason)
        // Weaker evidence than a stated employer, and labelled as such.
        assertEquals(CategorySuggestion.Confidence.MEDIUM, suggestion.confidence)
    }

    /** A consumer address alongside a work one doesn't hide the work domain */
    @Test
    fun mixedEmailsFindTheWorkDomain() {
        assertEquals("acme.co", ContactCategorizer.workEmailDomain(listOf("me@gmail.com", "me@acme.co")))
        assertNull(ContactCategorizer.workEmailDomain(listOf("me@gmail.com")))
        assertNull(ContactCategorizer.workEmailDomain(listOf("not-an-email")))
    }

    // MARK: - Personal

    /** A family relation on the card outranks an employer */
    @Test
    fun familyBeatsEmployer() {
        val summary = contact(
            organization = "Some Company",
            jobTitle = "Manager",
            relations = listOf(ContactRelation(label = "mother", name = "Linda Clark")),
        )
        val suggestion = assertNotNull(ContactCategorizer.suggestion(summary))

        assertEquals(RelationshipContext.PERSONAL, suggestion.context)
        assertEquals("Family", suggestion.tagName)
        assertEquals("Listed as mother on the contact card", suggestion.reason)
        assertEquals(CategorySuggestion.Confidence.HIGH, suggestion.confidence)
    }

    /** Every family relation label is recognised (parameterized in Swift) */
    @Test
    fun familyLabels() {
        val labels = listOf(
            "mother", "father", "brother", "sister", "spouse", "wife", "husband",
            "son", "daughter", "parent", "child", "partner",
        )
        for (label in labels) {
            val summary = contact(relations = listOf(ContactRelation(label = label, name = "Someone")))
            val suggestion = assertNotNull(ContactCategorizer.suggestion(summary), label)
            assertEquals("Family", suggestion.tagName, label)
        }
    }

    /** An unrelated relation label like 'assistant' isn't treated as family */
    @Test
    fun nonFamilyRelationsAreIgnored() {
        val summary = contact(relations = listOf(ContactRelation(label = "assistant", name = "Someone")))
        assertNull(ContactCategorizer.suggestion(summary))
    }

    // MARK: - Batch

    /** Batch suggestions key by identifier and omit contacts that can't be placed */
    @Test
    fun batchOmitsUnplaceable() {
        val placeable = contact(given = "Sarah", organization = "Coastal Design Co.")
        val unplaceable = contact(given = "Alex")

        val result = ContactCategorizer.suggestions(listOf(placeable, unplaceable))

        assertEquals(1, result.size)
        assertEquals(RelationshipContext.BUSINESS, result[placeable.identifier]?.context)
        assertNull(result[unplaceable.identifier])
    }
}
