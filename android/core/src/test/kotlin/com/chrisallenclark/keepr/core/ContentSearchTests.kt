package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.MemoryEngine
import com.chrisallenclark.keepr.core.domain.PeopleEngine
import com.chrisallenclark.keepr.core.domain.SearchField
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.MemoryCategory
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.services.HeuristicCaptureExtractor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Translated from KeeprTests/ContentSearchTests.swift ("Searching by what you
// know" and "Memory categories").

/**
 * The People list has to find people by what you know about them, not just by
 * what they're called. Someone searching "soccer" is looking for the client
 * whose daughter plays soccer — they already know they don't have a contact
 * named Soccer.
 */
class ContentSearchTests {

    /** A word from a memory finds the person */
    @Test
    fun memoryContentMatches() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake", relationship = RelationshipContext.BUSINESS)
        Make.memory(
            store.context,
            content = "Daughter plays travel soccer on weekends",
            category = MemoryCategory.FAMILY,
            person = person,
        )

        assertTrue(PeopleEngine.matches(person, "soccer"))
    }

    /** The row can say why they matched */
    @Test
    fun matchReasonIsReturned() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake", relationship = RelationshipContext.BUSINESS)
        Make.memory(
            store.context,
            content = "Daughter plays travel soccer on weekends",
            person = person,
        )

        val reason = assertNotNull(PeopleEngine.reason(person, "soccer"))
        assertEquals(SearchField.MEMORY, reason.field)
        assertEquals("Daughter plays travel soccer on weekends", reason.snippet)
    }

    /** A name match needs no explanation */
    @Test
    fun nameMatchesOutrankContent() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake", family = "Soccer")
        Make.memory(store.context, content = "Plays soccer", person = person)

        assertEquals(SearchField.NAME, PeopleEngine.reason(person, "soccer")?.field)
    }

    /** Searching the People list reaches memories, not just names */
    @Test
    fun peopleListFiltersOnContent() {
        val store = TestStore()
        val withMemory = Make.person(store.context, given = "Jake", relationship = RelationshipContext.BUSINESS)
        Make.memory(store.context, content = "Son plays soccer", person = withMemory)
        val without = Make.person(store.context, given = "Dana", relationship = RelationshipContext.BUSINESS)

        val results = PeopleEngine.filter(
            listOf(withMemory, without),
            mode = ContextMode.BUSINESS,
            query = "soccer",
        )

        assertEquals(listOf("Jake"), results.map { it.givenName })
    }

    /** An interaction note is searchable too */
    @Test
    fun interactionNotesMatch() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake")
        Make.interaction(
            store.context,
            rawNote = "Talked about his bad knee before the squat session",
            person = person,
        )

        val reason = assertNotNull(PeopleEngine.reason(person, "knee"))
        assertEquals(SearchField.INTERACTION, reason.field)
    }

    /** A file is found by what you named it */
    @Test
    fun fileTitlesMatch() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake")
        Make.file(store.context, title = "InBody scan week 1", person = person)

        val reason = assertNotNull(PeopleEngine.reason(person, "inbody"))
        assertEquals(SearchField.FILE, reason.field)
        assertEquals("InBody scan week 1", reason.snippet)
    }

    /** An empty query explains nothing rather than everything */
    @Test
    fun emptyQueryHasNoReason() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake")

        assertNull(PeopleEngine.reason(person, "   "))
    }

    /** Matches are case- and accent-insensitive */
    @Test
    fun matchingIsForgiving() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake")
        Make.memory(store.context, content = "Loves Pilates", person = person)

        assertTrue(PeopleEngine.matches(person, "pilates"))
        assertTrue(PeopleEngine.matches(person, "PILATES"))
    }

    /** Nobody matches a word nobody mentioned */
    @Test
    fun irrelevantQueryFindsNothing() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake")
        Make.memory(store.context, content = "Owns a roofing company", person = person)

        assertFalse(PeopleEngine.matches(person, "lacrosse"))
        assertNull(PeopleEngine.reason(person, "lacrosse"))
    }

    /** Every search-field symbol is a real SF Symbol */
    @Test
    fun fieldSymbolsExist() {
        for (field in SearchField.entries) {
            assertTrue(AndroidSymbols.exists(field.symbolName), "${field.symbolName} does not exist")
        }
    }
}

/**
 * The categories a fact can land in. Added because a trainer's brain dump was
 * filing injuries, availability and wins under "Other", which is the same as
 * not filing them.
 */
class MemoryCategoryTests {

    private val extractor = HeuristicCaptureExtractor()

    private fun category(text: String): MemoryCategory = HeuristicCaptureExtractor.category(text)

    /** An injury is health, not other */
    @Test
    fun injuriesAreHealth() {
        assertEquals(MemoryCategory.HEALTH, category("bad knee from college football"))
        assertEquals(MemoryCategory.HEALTH, category("had shoulder surgery two years ago"))
        assertEquals(MemoryCategory.HEALTH, category("allergic to shellfish"))
    }

    /** A win is progress, and a plan is still a goal */
    @Test
    fun progressIsNotGoals() {
        assertEquals(MemoryCategory.PROGRESS, category("lost 22 pounds since January"))
        assertEquals(MemoryCategory.GOALS, category("wants to lose 20 pounds before the wedding"))
    }

    /** When they can train is its own shelf */
    @Test
    fun availabilityIsSchedule() {
        assertEquals(MemoryCategory.SCHEDULE, category("only trains mornings before work"))
        assertEquals(MemoryCategory.SCHEDULE, category("travels every other week for work"))
    }

    /** An introduction they could make is an opportunity */
    @Test
    fun introductionsAreOpportunities() {
        assertEquals(MemoryCategory.OPPORTUNITY, category("could introduce me to his gym owner friend"))
        assertEquals(MemoryCategory.OPPORTUNITY, category("knows someone at the hospital"))
    }

    /** How someone takes things is worth its own shelf */
    @Test
    fun communicationStyleIsCaptured() {
        assertEquals(MemoryCategory.STYLE, category("blunt, no small talk"))
        assertEquals(MemoryCategory.STYLE, category("never answers calls, responds to texts fast"))
    }

    /** Work still wins for an ordinary work fact */
    @Test
    fun workStillWorks() {
        assertEquals(MemoryCategory.WORK, category("owns a roofing company"))
    }

    /** Nothing recognizable still lands in other rather than guessing */
    @Test
    fun unknownStaysOther() {
        assertEquals(MemoryCategory.OTHER, category("purple front door"))
    }

    /** Every category has a symbol, a title and an explanation */
    @Test
    fun categoriesAreFullyDescribed() {
        for (category in MemoryCategory.entries) {
            assertTrue(AndroidSymbols.exists(category.symbolName), "${category.symbolName} missing")
            assertFalse(category.title.isEmpty())
            assertFalse(category.explanation.isEmpty())
        }
    }

    /** Every category appears exactly once in the display order */
    @Test
    fun orderCoversEveryCategory() {
        val ordered = MemoryEngine.categoryOrder.toSet()

        assertEquals(MemoryEngine.categoryOrder.size, ordered.size, "no category listed twice")
        assertEquals(MemoryCategory.entries.toSet(), ordered, "a missing category would never be shown")
    }

    /** A dump from a consultation spreads across the new shelves */
    @Test
    fun realDumpIsSpread() = runTest {
        val dump = """
            Owns a roofing company with nine guys
            Bad lower back, cleared by his doctor for deadlifts
            Lost 14 pounds since we started
            Only trains before 7am
            Could introduce me to the gym owner in Maple Grove
            Blunt, hates being sold to
        """.trimIndent()

        val facts = extractor.facts(dump)
        val categories = facts.map { it.category }.toSet()

        assertEquals(6, facts.size, "every line has to survive")
        assertTrue(categories.contains(MemoryCategory.HEALTH))
        assertTrue(categories.contains(MemoryCategory.PROGRESS))
        assertTrue(categories.contains(MemoryCategory.OPPORTUNITY))
        assertTrue(categories.contains(MemoryCategory.STYLE))
        assertFalse(categories.contains(MemoryCategory.OTHER), "none of these should be unfiled")
    }

    // MARK: - Re-filing

    /** Facts stuck in Other are offered a home */
    @Test
    fun refileFindsMisfiledFacts() {
        val store = TestStore()
        val person = Make.person(store.context)
        val injury = Make.memory(
            store.context,
            content = "Bad knee, no running",
            category = MemoryCategory.OTHER,
            person = person,
        )
        val nonsense = Make.memory(
            store.context,
            content = "Purple front door",
            category = MemoryCategory.OTHER,
            person = person,
        )

        val proposals = MemoryEngine.refilable(person.memoryList) {
            HeuristicCaptureExtractor.category(it)
        }

        val touchesNonsense = proposals.any { it.memory.id == nonsense.id }

        assertEquals(1, proposals.size)
        assertEquals(injury.id, proposals.firstOrNull()?.memory?.id)
        assertEquals(MemoryCategory.HEALTH, proposals.firstOrNull()?.category)
        assertFalse(touchesNonsense)
    }

    /** Facts the user already filed by hand are left alone */
    @Test
    fun refileNeverOverwritesADecision() {
        val store = TestStore()
        val person = Make.person(store.context)
        Make.memory(
            store.context,
            content = "Owns a roofing company",
            category = MemoryCategory.PERSONAL,
            person = person,
        )

        val proposals = MemoryEngine.refilable(person.memoryList) {
            HeuristicCaptureExtractor.category(it)
        }

        assertTrue(proposals.isEmpty(), "a deliberate filing decision is not a mistake to repair")
    }

    /** Archived facts aren't dragged back into view */
    @Test
    fun refileSkipsArchived() {
        val store = TestStore()
        val person = Make.person(store.context)
        Make.memory(
            store.context,
            content = "Bad knee",
            category = MemoryCategory.OTHER,
            isArchived = true,
            person = person,
        )

        val proposals = MemoryEngine.refilable(person.memoryList) {
            HeuristicCaptureExtractor.category(it)
        }

        assertTrue(proposals.isEmpty())
    }
}
