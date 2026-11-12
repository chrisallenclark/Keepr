package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.domain.PeopleEngine
import com.aulorvy.keepr.core.domain.SearchEngine
import com.aulorvy.keepr.core.domain.SearchField
import com.aulorvy.keepr.core.model.ContextMode
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.model.TagKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Translated from KeeprTests/SearchEngineTests.swift.
class SearchEngineTests {

    /**
     * Builds one person per [SearchField], each matching "delta" through a
     * different route and nothing else.
     */
    private fun makeRankingFixture(store: TestStore): List<Person> {
        val context = store.context

        val byName = Make.person(
            context,
            given = "Delta",
            family = "Nguyen",
            company = "Acme",
            jobTitle = "Engineer",
        )
        val byCompany = Make.person(context, given = "Zoe", family = "Quinn", company = "Delta Corp")
        val byTag = Make.person(context, given = "Yara", family = "Reed")
        Make.attach(Make.tag(context, name = "Delta Team", kind = TagKind.BUSINESS), to = byTag)

        val byMemory = Make.person(context, given = "Xavier", family = "Stone")
        Make.memory(context, content = "Flies delta routes weekly", person = byMemory)

        val byInteraction = Make.person(context, given = "Wes", family = "Turner")
        Make.interaction(context, summary = "Reviewed delta pricing", person = byInteraction)

        val byNote = Make.person(
            context,
            given = "Vera",
            family = "Upton",
            notes = "Prefers delta over gamma",
        )

        val byWriteUp = Make.person(context, given = "Xena", family = "Royce")
        Make.note(
            context,
            title = "Kickoff",
            body = "Agreed the delta milestones before anything else.",
            person = byWriteUp,
        )

        val byFile = Make.person(context, given = "Wendy", family = "Sharp")
        Make.file(context, title = "Delta scan week 1", person = byFile)

        val byFollowUp = Make.person(context, given = "Uma", family = "Vance")
        Make.followUp(context, title = "Send delta report", due = TestDates.dayStart(2), person = byFollowUp)

        return listOf(
            byFollowUp, byNote, byFile, byInteraction, byWriteUp,
            byMemory, byTag, byCompany, byName,
        )
    }

    // MARK: Ranking

    /** Fields rank identity, then memories, notes, interactions, files, details, follow-ups */
    @Test
    fun fieldRanking() {
        val store = TestStore()
        val people = makeRankingFixture(store)

        val results = SearchEngine.search("delta", people)

        assertEquals(
            listOf(
                SearchField.NAME, SearchField.COMPANY, SearchField.TAG, SearchField.MEMORY, SearchField.WRITE_UP,
                SearchField.INTERACTION, SearchField.FILE, SearchField.NOTE, SearchField.FOLLOW_UP,
            ),
            results.map { it.field },
        )
    }

    /** Within one field, results are ordered by sort key */
    @Test
    fun sameFieldOrderedBySortKey() {
        val store = TestStore()
        val baker = Make.person(store.context, given = "Delta", family = "Baker")
        val adams = Make.person(store.context, given = "Delta", family = "Adams")

        val results = SearchEngine.search("delta", listOf(baker, adams))
        assertEquals(listOf(adams.id, baker.id), results.map { it.person.id })
    }

    // MARK: Field-by-field matching

    /** A preferred-name match counts as a name match, with the subtitle as snippet */
    @Test
    fun preferredNameMatch() {
        val store = TestStore()
        val person = Make.person(
            store.context,
            given = "Michael",
            family = "Nguyen",
            preferred = "Mikey",
            company = "Acme",
            jobTitle = "Owner",
        )

        val result = assertNotNull(SearchEngine.bestMatch(person, query = "mikey"))
        assertEquals(SearchField.NAME, result.field)
        assertEquals("Owner · Acme", result.snippet)
    }

    /** A job title match counts as a company match */
    @Test
    fun jobTitleMatch() {
        val store = TestStore()
        val person = Make.person(
            store.context,
            given = "Zoe",
            family = "Quinn",
            company = "Acme",
            jobTitle = "Roofing Estimator",
        )

        val result = assertNotNull(SearchEngine.bestMatch(person, query = "estimator"))
        assertEquals(SearchField.COMPANY, result.field)
        assertEquals("Roofing Estimator · Acme", result.snippet)
    }

    /** A tag match reports the tag name as its snippet */
    @Test
    fun tagMatch() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Yara", family = "Reed")
        Make.attach(Make.tag(store.context, name = "Referral Source", kind = TagKind.BUSINESS), to = person)

        val result = assertNotNull(SearchEngine.bestMatch(person, query = "referral"))
        assertEquals(SearchField.TAG, result.field)
        assertEquals("Referral Source", result.snippet)
    }

    /** A memory match reports the memory content as its snippet */
    @Test
    fun memoryMatch() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Xavier", family = "Stone")
        Make.memory(store.context, content = "Owns a roofing company", person = person)

        val result = assertNotNull(SearchEngine.bestMatch(person, query = "roofing"))
        assertEquals(SearchField.MEMORY, result.field)
        assertEquals("Owns a roofing company", result.snippet)
    }

    /** An interaction summary match reports the interaction headline */
    @Test
    fun interactionSummaryMatch() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Wes", family = "Turner")
        Make.interaction(store.context, summary = "Talked about pricing", person = person)

        val result = assertNotNull(SearchEngine.bestMatch(person, query = "pricing"))
        assertEquals(SearchField.INTERACTION, result.field)
        assertEquals("Talked about pricing", result.snippet)
    }

    /** An interaction raw note match reports the interaction headline */
    @Test
    fun interactionRawNoteMatch() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Wes", family = "Turner")
        Make.interaction(store.context, rawNote = "He mentioned the warehouse move", person = person)

        val result = assertNotNull(SearchEngine.bestMatch(person, query = "warehouse"))
        assertEquals(SearchField.INTERACTION, result.field)
        assertEquals("He mentioned the warehouse move", result.snippet)
    }

    /** A notes match reports the note as its snippet */
    @Test
    fun notesMatch() {
        val store = TestStore()
        val person = Make.person(
            store.context,
            given = "Vera",
            family = "Upton",
            notes = "Allergic to shellfish",
        )

        val result = assertNotNull(SearchEngine.bestMatch(person, query = "shellfish"))
        assertEquals(SearchField.NOTE, result.field)
        assertEquals("Allergic to shellfish", result.snippet)
    }

    /** A howWeMet match is a note match and falls back to howWeMet for the snippet */
    @Test
    fun howWeMetMatch() {
        val store = TestStore()
        val person = Make.person(
            store.context,
            given = "Vera",
            family = "Upton",
            howWeMet = "Met at the Denver trade show",
        )

        val result = assertNotNull(SearchEngine.bestMatch(person, query = "trade show"))
        assertEquals(SearchField.NOTE, result.field)
        assertEquals("Met at the Denver trade show", result.snippet)
    }

    /** A follow-up note match still reports the follow-up title as its snippet */
    @Test
    fun followUpMatch() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Uma", family = "Vance")
        Make.followUp(
            store.context,
            title = "Ping about the numbers",
            note = "mention the skylight quote",
            due = TestDates.dayStart(2),
            person = person,
        )

        val result = assertNotNull(SearchEngine.bestMatch(person, query = "skylight"))
        assertEquals(SearchField.FOLLOW_UP, result.field)
        assertEquals("Ping about the numbers", result.snippet)
    }

    /** A person who matches nothing produces no result */
    @Test
    fun noMatch() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Ann", family = "Adams")

        assertNull(SearchEngine.bestMatch(person, query = "zzzz"))
        assertTrue(SearchEngine.search("zzzz", listOf(person)).isEmpty())
    }

    /** Search is case-insensitive */
    @Test
    fun searchIgnoresCase() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Xavier", family = "Stone")
        Make.memory(store.context, content = "Owns a roofing company", person = person)

        assertEquals(1, SearchEngine.search("ROOFING", listOf(person)).size)
    }

    // MARK: Context independence

    /** Search ignores the business/personal switch */
    @Test
    fun searchIgnoresContextMode() {
        val store = TestStore()
        val workPerson = Make.person(
            store.context,
            given = "Delta",
            family = "Nguyen",
            relationship = RelationshipContext.BUSINESS,
        )
        val homePerson = Make.person(
            store.context,
            given = "Hal",
            family = "Home",
            relationship = RelationshipContext.PERSONAL,
        )
        val people = listOf(workPerson, homePerson)

        // The People list would hide the business person in personal mode…
        assertEquals(listOf(homePerson.id), PeopleEngine.visible(people, ContextMode.PERSONAL).map { it.id })
        // …but search still finds them.
        assertEquals(listOf(workPerson.id), SearchEngine.search("delta", people).map { it.person.id })
    }

    // MARK: Empty queries

    /** An empty or whitespace-only query returns nothing */
    @Test
    fun blankQueryReturnsNothing() {
        val store = TestStore()
        val people = makeRankingFixture(store)

        assertTrue(SearchEngine.search("", people).isEmpty())
        assertTrue(SearchEngine.search("   ", people).isEmpty())
        assertTrue(SearchEngine.search("\n\t ", people).isEmpty())
    }

    // MARK: grouped

    /** grouped follows SearchField.allCases order and drops empty groups */
    @Test
    fun groupedOrderAndEmptyGroups() {
        val store = TestStore()
        val people = makeRankingFixture(store)
        val results = SearchEngine.search("delta", people)

        val groups = SearchEngine.grouped(results)
        assertEquals(SearchField.entries.toList(), groups.map { it.field })
        assertTrue(groups.all { it.results.size == 1 })
    }

    /** grouped omits fields nothing matched on */
    @Test
    fun groupedDropsUnmatchedFields() {
        val store = TestStore()
        val byName = Make.person(store.context, given = "Delta", family = "Nguyen")
        val byMemory = Make.person(store.context, given = "Xavier", family = "Stone")
        Make.memory(store.context, content = "Flies delta routes", person = byMemory)

        val groups = SearchEngine.grouped(SearchEngine.search("delta", listOf(byMemory, byName)))
        assertEquals(listOf(SearchField.NAME, SearchField.MEMORY), groups.map { it.field })
        assertEquals(listOf(byName.id), groups.firstOrNull()?.results?.map { it.person.id })
    }

    /** SearchField ranks by declaration order */
    @Test
    fun searchFieldComparable() {
        assertTrue(SearchField.NAME < SearchField.COMPANY)
        assertTrue(SearchField.COMPANY < SearchField.TAG)
        assertTrue(SearchField.TAG < SearchField.MEMORY)
        assertTrue(SearchField.MEMORY < SearchField.WRITE_UP)
        assertTrue(SearchField.WRITE_UP < SearchField.INTERACTION)
        assertTrue(SearchField.INTERACTION < SearchField.FILE)
        assertTrue(SearchField.FILE < SearchField.NOTE)
        assertTrue(SearchField.NOTE < SearchField.FOLLOW_UP)
    }
}
