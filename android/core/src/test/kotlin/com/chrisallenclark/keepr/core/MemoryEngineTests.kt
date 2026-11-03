package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.MemoryEngine
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.MemoryCategory
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.Priority
import com.chrisallenclark.keepr.core.model.localizedCaseInsensitiveContains
import com.chrisallenclark.keepr.core.services.HeuristicCaptureExtractor
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Translated from KeeprTests/MemoryEngineTests.swift.

/**
 * A brain dump can put fifteen facts on someone in one go. What decides whether
 * that helps or ruins the profile is what gets shown and what gets filed — so
 * these are the rules that keep a full record readable.
 */
class MemoryEngineTests {

    private fun memory(
        store: TestStore,
        content: String,
        category: MemoryCategory,
        importance: Priority = Priority.NORMAL,
        createdAt: Instant = TestDates.now,
        person: Person,
    ): Memory = Make.memory(
        store.context,
        content = content,
        category = category,
        importance = importance,
        createdAt = createdAt,
        person = person,
    )

    // MARK: - What the profile shows

    @Test
    fun headlineIsCapped() {
        // The profile shows a handful, not everything
        val store = TestStore()
        val person = Make.person(store.context)
        for (index in 0 until 12) {
            memory(store, "Fact $index", MemoryCategory.OTHER, person = person)
        }

        assertEquals(MemoryEngine.headlineLimit, MemoryEngine.headline(person.visibleMemories).size)
    }

    @Test
    fun headlineSpansCategories() {
        // It spreads across categories, so it reads like a person
        val store = TestStore()
        val person = Make.person(store.context)
        // Three family facts added most recently would otherwise crowd out
        // everything else and make the profile look like a family tree.
        memory(store, "Two kids", MemoryCategory.FAMILY, createdAt = TestDates.days(-1), person = person)
        memory(store, "Wife is Ana", MemoryCategory.FAMILY, createdAt = TestDates.days(-1), person = person)
        memory(store, "Sister nearby", MemoryCategory.FAMILY, createdAt = TestDates.days(-1), person = person)
        memory(store, "Runs a roofing crew", MemoryCategory.WORK, createdAt = TestDates.days(-2), person = person)
        memory(store, "Wants to lose 20 lb", MemoryCategory.GOALS, createdAt = TestDates.days(-3), person = person)

        val categories = MemoryEngine.headline(person.visibleMemories).map { it.category }.toSet()

        assertEquals(3, categories.size, "one from each of three categories")
        assertTrue(categories.contains(MemoryCategory.WORK))
        assertTrue(categories.contains(MemoryCategory.GOALS))
    }

    @Test
    fun starredWinsFirstPlace() {
        // Starred facts come first, whatever their category
        val store = TestStore()
        val person = Make.person(store.context)
        memory(store, "Ordinary", MemoryCategory.WORK, createdAt = TestDates.now, person = person)
        val starred = memory(
            store,
            "Allergic to shellfish",
            MemoryCategory.PREFERENCES,
            importance = Priority.HIGH,
            createdAt = TestDates.days(-30),
            person = person,
        )

        assertEquals(starred.id, MemoryEngine.headline(person.visibleMemories).firstOrNull()?.id)
    }

    @Test
    fun repeatsAreAllowedOnceEveryCategoryHasATurn() {
        // With only one category, depth is allowed rather than showing one line
        val store = TestStore()
        val person = Make.person(store.context)
        for (index in 0 until 4) {
            memory(store, "Work fact $index", MemoryCategory.WORK, person = person)
        }

        assertEquals(3, MemoryEngine.headline(person.visibleMemories).size)
    }

    @Test
    fun archivedIsNotShown() {
        // Archived facts are held back from the profile
        val store = TestStore()
        val person = Make.person(store.context)
        val archived = Make.memory(
            store.context,
            content = "Old news",
            isArchived = true,
            person = person,
        )

        assertTrue(MemoryEngine.headline(listOf(archived)).isEmpty())
        assertTrue(MemoryEngine.grouped(listOf(archived)).isEmpty())
    }

    // MARK: - The full list

    @Test
    fun groupsFollowReadingOrder() {
        // Everything is filed by category, in reading order
        val store = TestStore()
        val person = Make.person(store.context)
        memory(store, "Loves fishing", MemoryCategory.INTERESTS, person = person)
        memory(store, "Runs a roofing crew", MemoryCategory.WORK, person = person)
        memory(store, "Two kids", MemoryCategory.FAMILY, person = person)

        val groups = MemoryEngine.grouped(person.visibleMemories)

        assertEquals(listOf(MemoryCategory.WORK, MemoryCategory.FAMILY, MemoryCategory.INTERESTS), groups.map { it.category })
        val oneEach = groups.all { it.count == 1 }
        assertTrue(oneEach)
    }

    @Test
    fun groupsSortByImportance() {
        // Within a category, starred facts sit at the top
        val store = TestStore()
        val person = Make.person(store.context)
        memory(store, "Plain", MemoryCategory.WORK, createdAt = TestDates.now, person = person)
        memory(store, "Crucial", MemoryCategory.WORK, importance = Priority.HIGH, createdAt = TestDates.days(-40), person = person)

        val group = assertNotNull(MemoryEngine.grouped(person.visibleMemories).firstOrNull())

        assertEquals("Crucial", group.memories.firstOrNull()?.content)
    }

    @Test
    fun emptyCategoriesAreDropped() {
        // Empty categories aren't shown as zeroes
        val store = TestStore()
        val person = Make.person(store.context)
        memory(store, "Runs a roofing crew", MemoryCategory.WORK, person = person)

        assertEquals(1, MemoryEngine.grouped(person.visibleMemories).size)
    }

    @Test
    fun totalsReadNaturally() {
        // Counts are written the way they'll be read
        assertEquals("1 thing", MemoryEngine.totalLabel(1))
        assertEquals("14 things", MemoryEngine.totalLabel(14))
    }
}

/**
 * The brain dump has a different contract from a quick note: a note should
 * suggest little and stay quiet when unsure, but a dump has to keep everything,
 * because facts silently dropped are worse than none at all.
 */
class BrainDumpExtractionTests {

    private val extractor = HeuristicCaptureExtractor()

    @Test
    fun nothingIsSilentlyDropped() = runTest {
        // Every line survives, including ones no rule recognizes
        val dump = """
            Runs a roofing crew of nine
            Daughter getting married in the spring
            Purple front door
        """.trimIndent()

        val facts = extractor.facts(dump)

        assertEquals(3, facts.size)
        assertTrue(facts.any { it.content.contains("Purple front door") })
    }

    @Test
    fun spokenInputIsSplit() = runTest {
        // Dictation with no punctuation still comes apart
        val spoken = "he owns a roofing company and he's got two kids " +
            "also he wants to lose 20 pounds before the wedding"

        val facts = extractor.facts(spoken)

        assertTrue(facts.size >= 3, "spoken joins have to count as breaks")
        assertTrue(facts.any { it.content.localizedCaseInsensitiveContains("roofing") })
        assertTrue(facts.any { it.content.localizedCaseInsensitiveContains("20 pounds") })
    }

    @Test
    fun categoriesAreAssigned() = runTest {
        // Facts land in sensible categories
        val dump = """
            Owns a roofing company
            Two kids, 7 and 10
            Wants to lose 20 lb
            Allergic to shellfish
            Prefers black coffee
            Birthday is March 14
        """.trimIndent()

        val facts = extractor.facts(dump)
        val byCategory = facts.groupBy { it.category }

        assertEquals(false, byCategory[MemoryCategory.WORK]?.isEmpty())
        assertEquals(false, byCategory[MemoryCategory.FAMILY]?.isEmpty())
        assertEquals(false, byCategory[MemoryCategory.GOALS]?.isEmpty())
        assertEquals(false, byCategory[MemoryCategory.HEALTH]?.isEmpty(), "an allergy is health, not a preference")
        assertEquals(false, byCategory[MemoryCategory.PREFERENCES]?.isEmpty())
        assertEquals(false, byCategory[MemoryCategory.IMPORTANT_DATE]?.isEmpty())
    }

    @Test
    fun shortListsAreNotOverSplit() = runTest {
        // A short list with commas stays one fact, not three
        val facts = extractor.facts("Two kids, 7 and 10")

        assertEquals(1, facts.size)
    }

    @Test
    fun draftsStartSelected() = runTest {
        // Everything arrives switched on, ready to keep
        val facts = extractor.facts("Runs a roofing crew\nLoves fishing")

        val allOn = facts.all { it.isSelected }
        assertTrue(allOn)
    }

    @Test
    fun emptyInputIsEmpty() = runTest {
        // An empty dump produces nothing rather than an empty card
        assertTrue(extractor.facts("   \n  ").isEmpty())
    }

    @Test
    fun longDumpsSurvive() = runTest {
        // A long dump isn't truncated to a handful
        val lines = (1..20).joinToString("\n") { "Fact number $it about them" }

        val facts = extractor.facts(lines)

        assertEquals(20, facts.size)
    }
}
