package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.MemoryCategory
import com.chrisallenclark.keepr.core.services.HeuristicCaptureExtractor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Translated from KeeprTests/CaptureExtractorTests.swift.

/**
 * The on-device extractor is the most rule-heavy code in the app and the part a
 * future model-backed extractor has to match, so its behaviour is pinned here.
 */
class CaptureExtractorTests {

    private val extractor = HeuristicCaptureExtractor()
    private val calendar = TestDates.calendar
    private val now = TestDates.now

    /** The example the product is designed around. */
    private val canonical =
        "Talked to Jake at Life Time. He owns a roofing company, wants to lose 20 pounds " +
            "and said to text him next week about training."

    // MARK: - Word boundaries

    @Test
    fun wordBoundaryMatching() {
        // Keywords match on word boundaries, not substrings
        val haystack = HeuristicCaptureExtractor.matchable("Told him something about it.")
        assertTrue(HeuristicCaptureExtractor.containsWord(haystack, "something"))
        // "something" contains the letters of "met" — that must not count.
        assertFalse(HeuristicCaptureExtractor.containsWord(haystack, "met"))
    }

    @Test
    fun matchableStripsPunctuation() {
        // Punctuation doesn't hide a keyword
        val haystack = HeuristicCaptureExtractor.matchable("We met, finally!")
        assertTrue(HeuristicCaptureExtractor.containsWord(haystack, "met"))
    }

    @Test
    fun kindDefaultsToOther() {
        // A note with no interaction verb stays Other rather than guessing
        assertEquals(InteractionKind.OTHER, HeuristicCaptureExtractor.kind("Told him something about pricing"))
    }

    @Test
    fun kindDetection() {
        // Interaction kind comes from the verb used
        val cases = listOf(
            "Called Sarah about the invoice" to InteractionKind.CALL,
            "Texted Mike the address" to InteractionKind.TEXT,
            "Emailed the proposal over" to InteractionKind.EMAIL,
            "Zoom with the team" to InteractionKind.VIDEO,
            "Grabbed lunch with Dana" to InteractionKind.MEAL,
            "Met him at the conference" to InteractionKind.EVENT,
            "Ran into Dana at the store" to InteractionKind.IN_PERSON,
        )
        for ((input, expected) in cases) {
            assertEquals(expected, HeuristicCaptureExtractor.kind(input), input)
        }
    }

    // MARK: - Names

    @Test
    fun personNameDetection() {
        // A capitalized name after a conversational verb is picked up
        assertEquals("Jake", HeuristicCaptureExtractor.personName(canonical))
        assertEquals("Sarah Miller", HeuristicCaptureExtractor.personName("Met with Sarah Miller today"))
        assertEquals("Mom", HeuristicCaptureExtractor.personName("Called Mom"))
    }

    @Test
    fun personNameAbsent() {
        // No name is suggested when nothing looks like one
        assertNull(HeuristicCaptureExtractor.personName("Need to order more coffee filters"))
        assertNull(HeuristicCaptureExtractor.personName("talked to the bank again"))
    }

    // MARK: - Segmentation

    @Test
    fun clauseSplitting() {
        // A sentence splits into its separate claims
        val clauses = HeuristicCaptureExtractor.clauses(
            "He owns a roofing company, wants to lose 20 pounds and said to text him next week.",
        )
        assertEquals(
            listOf(
                "He owns a roofing company",
                "wants to lose 20 pounds",
                "said to text him next week",
            ),
            clauses,
        )
    }

    @Test
    fun summaryIsFirstSentence() {
        // Summary is the first sentence
        assertEquals("Talked to Jake at Life Time.", HeuristicCaptureExtractor.summary(canonical))
    }

    @Test
    fun summaryTruncates() {
        // A long single sentence is truncated on a word boundary
        val long = "word ".repeat(60)
        val summary = HeuristicCaptureExtractor.summary(long, limit = 40)
        assertTrue(summary.length <= 41)
        assertTrue(summary.endsWith("…"))
    }

    // MARK: - Facts

    @Test
    fun memoryExtraction() {
        // Statements about the person become categorized memories
        val owns = assertNotNull(HeuristicCaptureExtractor.memory("He owns a roofing company"))
        assertEquals("Owns a roofing company", owns.content)
        assertEquals(MemoryCategory.WORK, owns.category)

        val goal = assertNotNull(HeuristicCaptureExtractor.memory("wants to lose 20 pounds"))
        assertEquals("Wants to lose 20 pounds", goal.content)
        assertEquals(MemoryCategory.GOALS, goal.category)

        val family = assertNotNull(HeuristicCaptureExtractor.memory("his daughter is getting married"))
        assertEquals(MemoryCategory.IMPORTANT_DATE, family.category)
    }

    @Test
    fun memoryRejectsNonFacts() {
        // A clause that states nothing about the person is not a memory
        assertNull(HeuristicCaptureExtractor.memory("it was a good chat"))
        assertNull(HeuristicCaptureExtractor.memory("ok"))
    }

    // MARK: - Actions

    @Test
    fun actionDetection() {
        // Promise phrasing is recognised as an action, statements aren't
        assertTrue(HeuristicCaptureExtractor.isAction("said to text him next week"))
        assertTrue(HeuristicCaptureExtractor.isAction("I need to send the proposal"))
        assertFalse(HeuristicCaptureExtractor.isAction("He owns a roofing company"))
    }

    @Test
    fun actionTitles() {
        // Action titles drop the lead-in and read as instructions
        assertEquals(
            "Text him next week about training",
            HeuristicCaptureExtractor.actionTitle("said to text him next week about training"),
        )
        assertEquals("Send the proposal", HeuristicCaptureExtractor.actionTitle("I need to send the proposal"))
    }

    // MARK: - Dates

    @Test
    fun relativeDueDates() {
        // Relative phrases resolve against the supplied now, at start of day
        val nextWeek = HeuristicCaptureExtractor.dueDate("text him next week", now, calendar)
        assertEquals(TestDates.dayStart(7), nextWeek)

        val tomorrow = HeuristicCaptureExtractor.dueDate("call her tomorrow", now, calendar)
        assertEquals(TestDates.dayStart(1), tomorrow)
    }

    @Test
    fun absentDueDate() {
        // No date phrase means no invented deadline
        assertNull(HeuristicCaptureExtractor.dueDate("text him about training", now, calendar))
    }

    @Test
    fun defaultDue() {
        // The default due date is three days out
        assertEquals(TestDates.dayStart(3), HeuristicCaptureExtractor.defaultDue(now, calendar))
    }

    // MARK: - End to end

    @Test
    fun canonicalExtraction() = runTest {
        // The canonical note becomes a person, two facts and a dated follow-up
        val draft = extractor.extract(canonical, now)

        assertEquals("Jake", draft.suggestedName)
        assertEquals("Talked to Jake at Life Time.", draft.summary)

        val contents = draft.memories.map { it.content }
        assertTrue(contents.contains("Owns a roofing company"))
        assertTrue(contents.contains("Wants to lose 20 pounds"))
        val everySuggestionIsPreselected = draft.memories.all { it.isSelected }
        assertTrue(everySuggestionIsPreselected)

        val followUp = assertNotNull(draft.followUp)
        assertEquals("Text him next week about training", followUp.title)
        assertEquals(false, followUp.hasTime)
        assertTrue(draft.hasSuggestions)
    }

    @Test
    fun emptyInput() = runTest {
        // Empty input produces an empty draft rather than a guess
        val draft = extractor.extract("   ", now)
        assertTrue(draft.summary.isEmpty())
        assertTrue(draft.memories.isEmpty())
        assertNull(draft.followUp)
        assertEquals(now, draft.occurredAt)
    }

    @Test
    fun draftsAreDeduplicated() = runTest {
        // Duplicate facts are collapsed and suggestions are capped
        val repeated = List(8) { "He owns a roofing company." }.joinToString(" ")
        val draft = extractor.extract(repeated, now)
        assertEquals(1, draft.memories.size)
    }
}
