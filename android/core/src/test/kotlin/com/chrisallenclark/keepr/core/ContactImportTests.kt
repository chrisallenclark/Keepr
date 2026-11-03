package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.MarkerVocabulary
import com.chrisallenclark.keepr.core.domain.ReviewQueue
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.MemoryCategory
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.persistence.CategorizeAnswer
import com.chrisallenclark.keepr.core.persistence.ContactImport
import com.chrisallenclark.keepr.core.persistence.KeeprStore
import com.chrisallenclark.keepr.core.persistence.ReviewActions
import com.chrisallenclark.keepr.core.services.ContactChangeTracker
import com.chrisallenclark.keepr.core.services.ContactDataRow
import com.chrisallenclark.keepr.core.services.ContactHeader
import com.chrisallenclark.keepr.core.services.ContactMimeType
import com.chrisallenclark.keepr.core.services.ContactRelation
import com.chrisallenclark.keepr.core.services.ContactRowAssembler
import com.chrisallenclark.keepr.core.services.ContactSummary
import com.chrisallenclark.keepr.core.domain.ContactCategorizer
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading Android contact rows, and the importer's and Catch Up's decisions. */
class ContactImportTests {

    private fun row(id: Long, type: String, vararg data: String?) = ContactDataRow(id, type, data.toList())

    private fun contact(
        identifier: String,
        given: String,
        family: String,
        organization: String? = null,
        title: String? = null,
        phones: List<String> = emptyList(),
        relations: List<ContactRelation> = emptyList(),
    ) = ContactSummary(identifier, given, family, organization, title, phones, emptyList(), relations = relations)

    // MARK: - Rows → summaries

    @Test
    fun assemblesEveryFieldKeeprReads() {
        val rows = listOf(
            row(7, ContactMimeType.NAME, "Jake Martinez", "Jake", "Martinez"),
            row(7, ContactMimeType.ORGANIZATION, "Martinez Roofing", "1", null, "Owner"),
            row(7, ContactMimeType.PHONE, "(561) 555-0142", "2"),
            row(7, ContactMimeType.PHONE, "(561) 555-0142", "1"),
            row(7, ContactMimeType.PHONE, "561-555-0199", "1"),
            row(7, ContactMimeType.EMAIL, "jake@martinezroofing.com"),
            row(7, ContactMimeType.EVENT, "1985-06-15", "3"),
            row(7, ContactMimeType.EVENT, "2010-09-01", "1"),
            row(7, ContactMimeType.POSTAL, "12 Ocean Ave\nDelray Beach, FL 33444"),
            row(7, ContactMimeType.RELATION, "Maria Martinez", "14"),
            row(7, ContactMimeType.RELATION, "Coach Dan", "0", "Trainer"),
            row(7, ContactMimeType.NOTE, "  Met at the expo  "),
            row(8, ContactMimeType.NAME, "Other", "Other", "Person"),
        )
        val photo = byteArrayOf(1, 2, 3)
        val summaries = ContactRowAssembler.assemble(
            listOf(ContactHeader(7, "lookup-7"), ContactHeader(8, "lookup-8")),
            rows,
            mapOf(7L to photo),
            ZoneOffset.UTC,
        )

        assertEquals(listOf("lookup-7", "lookup-8"), summaries.map { it.identifier })
        val jake = summaries[0]
        assertEquals("Jake", jake.givenName)
        assertEquals("Martinez", jake.familyName)
        assertEquals("Martinez Roofing", jake.organizationName)
        assertEquals("Owner", jake.jobTitle)
        assertEquals(listOf("(561) 555-0142", "561-555-0199"), jake.phoneNumbers, "duplicates from linked cards collapse")
        assertEquals(listOf("jake@martinezroofing.com"), jake.emailAddresses)
        assertEquals(LocalDate.of(1985, 6, 15).atStartOfDay(ZoneOffset.UTC).toInstant(), jake.birthday, "only the birthday event counts")
        assertEquals("12 Ocean Ave, Delray Beach, FL 33444", jake.postalAddress)
        assertEquals(listOf(ContactRelation("spouse", "Maria Martinez"), ContactRelation("Trainer", "Coach Dan")), jake.relations)
        assertEquals("Met at the expo", jake.note)
        assertTrue(jake.thumbnailData.contentEquals(photo))
        assertNull(summaries[1].thumbnailData)
        assertNull(summaries[1].organizationName)
    }

    @Test
    fun familyRelationOnAnAndroidCardIsReadAsFamily() {
        val summary = ContactRowAssembler.summary(
            ContactHeader(1, "k"),
            listOf(row(1, ContactMimeType.NAME, "Ann Lee", "Ann", "Lee"), row(1, ContactMimeType.RELATION, "Bo Lee", "8")),
        )
        val suggestion = assertNotNull(ContactCategorizer.suggestion(summary))
        assertEquals(RelationshipContext.PERSONAL, suggestion.context)
        assertEquals("Family", suggestion.tagName)
    }

    @Test
    fun aCardWithOnlyADisplayNameKeepsIt() {
        val summary = ContactRowAssembler.summary(ContactHeader(1, "k"), listOf(row(1, ContactMimeType.NAME, "Plumber Steve")))
        assertEquals("Plumber Steve", summary.fullName)

        val nameless = ContactRowAssembler.summary(ContactHeader(2, "k2"), listOf(row(2, ContactMimeType.ORGANIZATION, "Acme")))
        assertEquals("Acme", nameless.fullName, "falls back to the company, as iOS does")
    }

    @Test
    fun postalAddressFromPartsWhenNotFormatted() {
        val address = ContactRowAssembler.postalAddress(
            row(1, ContactMimeType.POSTAL, null, null, null, "1 Main St", null, null, "Boca Raton", "FL", "33431", "USA"),
        )
        assertEquals("1 Main St, Boca Raton, FL 33431, USA", address)
    }

    @Test
    fun birthdaysInEveryShapeAndroidWrites() {
        fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant()
        val utc = ZoneOffset.UTC
        assertEquals(day(1990, 6, 15), ContactRowAssembler.parseBirthday("1990-06-15", utc))
        assertEquals(day(1990, 6, 15), ContactRowAssembler.parseBirthday("1990-06-15T00:00:00.000Z", utc))
        assertEquals(day(1990, 6, 15), ContactRowAssembler.parseBirthday("19900615", utc))
        assertEquals(day(1990, 6, 15), ContactRowAssembler.parseBirthday("06/15/1990", utc))
        assertEquals(day(ContactRowAssembler.NO_YEAR, 6, 15), ContactRowAssembler.parseBirthday("--06-15", utc))
        assertEquals(day(ContactRowAssembler.NO_YEAR, 2, 29), ContactRowAssembler.parseBirthday("--0229", utc), "a leap day without a year survives")
        assertNull(ContactRowAssembler.parseBirthday("someday", utc))
        assertNull(ContactRowAssembler.parseBirthday("1990-13-40", utc))
    }

    // MARK: - Importing

    @Test
    fun importingCreatesPeopleAndNeverDuplicates() {
        val store = TestStore()
        val contacts = listOf(
            contact("a", "Jake", "Martinez", "Martinez Roofing", "Owner", listOf("(561) 555-0142")),
            contact("b", "Alex", "Nguyen"),
        )
        val created = ContactImport.importContacts(contacts, emptyMap(), true, ContextMode.PERSONAL, store.context, emptyList(), TestDates.calendar, TestDates.now)
        assertEquals(2, created.size)
        assertEquals(setOf("a", "b"), store.fetch<Person>().mapNotNull { it.contactIdentifier }.toSet())
        assertTrue(store.fetch<Person>().all { it.context == RelationshipContext.PERSONAL }, "no suggestion: the current lens decides")

        // A second pass — the same contacts again — adds no one.
        val again = ContactImport.importContacts(contacts, emptyMap(), true, ContextMode.PERSONAL, store.context, emptyList(), TestDates.calendar, TestDates.now)
        assertTrue(again.isEmpty())
        assertEquals(2, store.fetch<Person>().size)

        // And the importer shows them as added, so Select All skips them.
        val linked = ContactImport.linkedIdentifiers(store.fetch())
        assertTrue(ContactImport.selectAll(contacts, linked).isEmpty())
    }

    @Test
    fun aHandAddedPersonWithTheSameNameIsNotMerged() {
        // iOS links by contact identifier only; a matching name or number is
        // not proof it's the same person.
        val store = TestStore()
        Make.person(store.context, "Jake", "Martinez")
        val contacts = listOf(contact("a", "Jake", "Martinez", phones = listOf("(561) 555-0142")))
        assertEquals(setOf("a"), ContactImport.selectAll(contacts, ContactImport.linkedIdentifiers(store.fetch())))
        ContactImport.importContacts(contacts, emptyMap(), true, ContextMode.BUSINESS, store.context, emptyList(), TestDates.calendar)
        val jakes = store.fetch<Person>().filter { it.fullName == "Jake Martinez" }
        assertEquals(2, jakes.size)
        assertEquals(1, jakes.count { it.contactIdentifier == "a" })
    }

    @Test
    fun suggestionsApplyOnlyWhenSwitchedOn() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        val contacts = listOf(contact("a", "Jake", "Martinez", "Martinez Roofing", "Owner"))
        val suggestions = ContactImport.suggestions(contacts, MarkerVocabulary())
        assertEquals("Potential Client", suggestions["a"]?.tagName)

        val off = TestStore()
        ContactImport.importContacts(contacts, suggestions, false, ContextMode.PERSONAL, off.context, emptyList(), TestDates.calendar)
        val plain = off.fetch<Person>().single()
        assertEquals(RelationshipContext.PERSONAL, plain.context)
        assertTrue(plain.tagList.isEmpty())

        ContactImport.importContacts(contacts, suggestions, true, ContextMode.PERSONAL, store.context, emptyList(), TestDates.calendar)
        val placed = store.fetch<Person>().single()
        assertEquals(RelationshipContext.BUSINESS, placed.context)
        assertEquals(listOf("Potential Client"), placed.tagList.map { it.name })
    }

    @Test
    fun shorthandIsCleanedFromTheNameAndTheGroupJoined() {
        val store = TestStore()
        val group = PersonGroup("Life Time")
        store.context.insert(group)
        val vocabulary = MarkerVocabulary.build(listOf(group), emptyList())
        val card = contact("a", "Stanley", "LT")
        val suggestions = ContactImport.suggestions(listOf(card), vocabulary)
        val suggestion = assertNotNull(suggestions["a"])
        assertEquals("Stanley", ContactImport.displayName(card, suggestion, true))
        assertEquals("Stanley LT", ContactImport.displayName(card, suggestion, false))
        assertEquals("Life Time", ContactImport.suggestionLabel(suggestion))

        ContactImport.importContacts(listOf(card), suggestions, true, ContextMode.BUSINESS, store.context, listOf(group), TestDates.calendar)
        val person = store.fetch<Person>().single()
        assertEquals("Stanley", person.fullName)
        assertTrue(person.isMember(group))
        assertTrue(store.fetch<Memory>().any { it.content == "Contact card says: Stanley LT" })
    }

    @Test
    fun cardFactsBecomeMemories() {
        val store = TestStore()
        val card = ContactSummary(
            "a", "Ann", "Lee", null, null, emptyList(), emptyList(),
            birthday = TestDates.date(1990, 6, 15),
            relations = listOf(ContactRelation("mother", "Bo Lee")),
            note = "Allergic to shellfish",
        )
        ContactImport.importContacts(listOf(card), emptyMap(), true, ContextMode.PERSONAL, store.context, emptyList(), TestDates.calendar)
        val memories = store.fetch<Memory>().map { it.content to it.category }.toSet()
        assertEquals(
            setOf(
                "Birthday: June 15" to MemoryCategory.IMPORTANT_DATE,
                "Mother: Bo Lee" to MemoryCategory.FAMILY,
                "Allergic to shellfish" to MemoryCategory.OTHER,
            ),
            memories,
        )
    }

    @Test
    fun searchMatchesNameOrCompanyIgnoringCaseAndAccents() {
        val contacts = listOf(contact("a", "José", "Álvarez"), contact("b", "Sam", "Hill", "Coastal Design"), contact("c", "Kim", "Lo"))
        assertEquals(listOf("a"), ContactImport.results(contacts, "jose alv").map { it.identifier })
        assertEquals(listOf("b"), ContactImport.results(contacts, " coastal ").map { it.identifier })
        assertEquals(3, ContactImport.results(contacts, "  ").size)
    }

    @Test
    fun selectWithSuggestionsSkipsTheAlreadyAdded() {
        val contacts = listOf(
            contact("a", "Jake", "Martinez", "Martinez Roofing"),
            contact("b", "Sarah", "Miller", "Coastal Design"),
            contact("c", "Alex", "Nguyen"),
        )
        val suggestions = ContactImport.suggestions(contacts, MarkerVocabulary())
        assertEquals(listOf("b"), ContactImport.importableWithSuggestions(contacts, suggestions, setOf("a")).map { it.identifier })
    }

    // MARK: - Catch Up

    @Test
    fun acceptingANewContactImportsAndAcknowledgesIt() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        val tracker = ContactChangeTracker(InMemoryPreferences())
        tracker.newContacts(emptyList()) // baseline
        val card = contact("n1", "Dana", "Ross", "Ross & Co", "Founder")
        assertEquals(listOf(card), tracker.newContacts(listOf(card)))

        val item = ReviewQueue.items(listOf(card), store.fetch()).single()
        val person = ReviewActions.accept(item, assertNotNull(item.suggestion), store.context, emptyList(), tracker, TestDates.calendar, TestDates.now)
        assertEquals("n1", person.contactIdentifier)
        assertEquals(listOf("Potential Client"), person.tagList.map { it.name })
        assertTrue(tracker.newContacts(listOf(card)).isEmpty(), "never raised twice")
        assertTrue(ReviewQueue.items(listOf(card), store.fetch()).isEmpty())

        // Accepting the same stale item again categorizes, never duplicates.
        ReviewActions.accept(item, item.suggestion!!, store.context, emptyList(), tracker, TestDates.calendar, TestDates.now)
        assertEquals(1, store.fetch<Person>().size)
    }

    @Test
    fun notNowSticksForBothKinds() {
        val store = TestStore()
        val tracker = ContactChangeTracker(InMemoryPreferences())
        tracker.newContacts(emptyList())
        val card = contact("n1", "Dana", "Ross")
        val backlogged = Make.person(store.context, "Old", "Friend")

        val items = ReviewQueue.items(tracker.newContacts(listOf(card)), store.fetch())
        assertEquals(listOf(true, false), items.map { it.isNew })
        items.forEach { ReviewActions.skip(it, tracker, TestDates.now) }

        assertEquals(TestDates.now, backlogged.reviewedAt)
        assertTrue(ReviewQueue.items(tracker.newContacts(listOf(card)), store.fetch()).isEmpty())
        assertTrue(!ReviewQueue.needsCategorizing(backlogged))
    }

    @Test
    fun categorizingANewContactImportsItWithTheAnswers() {
        val store = TestStore()
        KeeprStore.seedIfNeeded(store.context, store.defaults)
        val tracker = ContactChangeTracker(InMemoryPreferences())
        tracker.newContacts(emptyList())
        val gym = PersonGroup("Life Time")
        store.context.insert(gym)
        val friend = store.fetch<RelationshipTag>().first { it.name == "Friend" }
        val card = contact("n1", "Dana", "Ross")
        val item = ReviewQueue.items(tracker.newContacts(listOf(card)), store.fetch()).single()

        val person = ReviewActions.categorize(
            item,
            CategorizeAnswer(
                context = RelationshipContext.PERSONAL,
                tag = friend,
                groups = listOf(gym),
                howWeMet = "  Met at Life Time ",
                memory = "Training for a marathon",
                interactionKind = InteractionKind.IN_PERSON,
                interactionNote = "",
            ),
            store.context, tracker, TestDates.calendar, TestDates.now,
        )
        assertEquals("n1", person.contactIdentifier)
        assertEquals(RelationshipContext.PERSONAL, person.context)
        assertEquals(listOf("Friend"), person.tagList.map { it.name })
        assertTrue(person.isMember(gym))
        assertEquals("Met at Life Time", person.howWeMet)
        assertEquals(TestDates.now, person.reviewedAt)
        assertEquals(TestDates.now, person.lastInteractionAt)
        assertEquals(InteractionKind.IN_PERSON, person.interactionList.single().kind)
        assertNull(person.interactionList.single().summary)
        assertEquals(listOf("Training for a marathon"), person.memoryList.map { it.content })
        assertTrue(tracker.newContacts(listOf(card)).isEmpty())
        assertTrue(ReviewQueue.items(listOf(card), store.fetch()).isEmpty())
    }

    @Test
    fun categorizingWithNoTypeStillCountsAsReviewed() {
        val store = TestStore()
        val tracker = ContactChangeTracker(InMemoryPreferences())
        val person = Make.person(store.context, "Old", "Friend")
        val item = ReviewQueue.items(emptyList(), store.fetch()).single()
        ReviewActions.categorize(item, CategorizeAnswer(RelationshipContext.BOTH), store.context, tracker, TestDates.calendar, TestDates.now)
        assertEquals(RelationshipContext.BOTH, person.context)
        assertTrue(person.interactionList.isEmpty())
        assertTrue(person.memoryList.isEmpty())
        assertTrue(ReviewQueue.items(emptyList(), store.fetch()).isEmpty())
    }
}
