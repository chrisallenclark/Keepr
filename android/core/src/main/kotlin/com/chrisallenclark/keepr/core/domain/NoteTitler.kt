package com.chrisallenclark.keepr.core.domain

import com.chrisallenclark.keepr.core.model.KeeprCalendar
import com.chrisallenclark.keepr.core.services.HeuristicCaptureExtractor
import com.chrisallenclark.keepr.core.services.characterCount
import com.chrisallenclark.keepr.core.services.graphemes
import com.chrisallenclark.keepr.core.services.isNewlineCharacter
import com.chrisallenclark.keepr.core.services.isWhitespaceCharacter
import com.chrisallenclark.keepr.core.services.isWhitespaceScalar
import com.chrisallenclark.keepr.core.services.prefixCharacters
import com.chrisallenclark.keepr.core.services.splitCharacters
import com.chrisallenclark.keepr.core.services.trimmingScalars
import com.chrisallenclark.keepr.core.services.trimmingWhitespaces
import com.chrisallenclark.keepr.core.services.trimmingWhitespacesAndNewlines
import java.time.Instant

// Translated from Keepr/Domain/NoteTitler.swift.

/**
 * Works out what to call a pasted write-up.
 *
 * The title is what makes a profile readable six months on, and it's also the
 * thing nobody wants to type. So this guesses, and the guess is always shown
 * in an editable field rather than applied silently.
 *
 * Three strategies, tried in order, because real pasted text falls into three
 * shapes:
 * 1. It already has a heading ("Consultation — Jake Mathers"). If the first
 *    line reads like a title, that *is* the title.
 * 2. It doesn't, but says what kind of conversation it was near the top.
 *    "Consultation · Mar 3" beats a sentence fragment.
 * 3. Neither. Fall back to the opening sentence, trimmed.
 */
object NoteTitler {

    /** Longest a first line can be and still read as a heading rather than prose. */
    const val headingLimit: Int = 64

    /**
     * How much of the text to search for a conversation kind. The whole thing
     * would match "consultation" in a closing sentence and mislabel the note.
     */
    const val scanLimit: Int = 400

    /** Conversation kinds, most specific first. The first hit wins. */
    val kinds: List<Pair<String, List<String>>> = listOf(
        "Consultation" to listOf("consultation", "consult", "intake", "assessment", "initial session"),
        "Check-In" to listOf("check in", "check-in", "checkin", "progress review", "weekly review"),
        "Discovery Call" to listOf("discovery call", "discovery"),
        "Sales Call" to listOf("sales call", "pitch", "proposal call", "quote"),
        "Onboarding" to listOf("onboarding", "kickoff", "kick off", "first session"),
        "Meeting" to listOf("meeting", "met with", "sat down"),
        "Call" to listOf("phone call", "call with", "spoke with", "called"),
        "Coffee" to listOf("coffee", "lunch", "dinner", "breakfast", "drinks"),
        "Training Session" to listOf("training session", "session", "workout", "lift"),
        "Interview" to listOf("interview"),
        "Review" to listOf("review", "recap", "debrief"),
    )

    /**
     * The suggested title, or "" when there's nothing to go on — callers then
     * fall back to the date alone rather than printing a guess made from two words.
     */
    fun suggest(text: String, date: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): String {
        val trimmed = text.trimmingWhitespacesAndNewlines()
        if (trimmed.isEmpty()) return ""

        heading(trimmed)?.let { return it }
        kind(trimmed)?.let { return "$it · ${day(date, calendar)}" }
        openingSentence(trimmed)?.let { return it }
        return "Notes · ${day(date, calendar)}"
    }

    // MARK: - Strategy 1: an existing heading

    /**
     * The first line, when it reads like a title rather than the start of a
     * paragraph: short, not ending in sentence punctuation, and more than one
     * word — a single word is usually a stray label like "Summary".
     */
    fun heading(text: String): String? {
        val first = text.splitCharacters(::isNewlineCharacter).firstOrNull() ?: return null

        var line = first.trimmingWhitespaces()
        // Markdown headings and bullets are headings with decoration.
        line = line.trimmingScalars { cp -> isWhitespaceScalar(cp) || "#*-•> ".indexOf(cp.toChar()) >= 0 && cp <= 0xFFFF }

        if (line.isEmpty() || line.characterCount > headingLimit) return null
        val last = line.graphemes().lastOrNull() ?: return null
        if (last.length == 1 && ".!?,;:".contains(last)) return null

        val words = line.splitCharacters(::isWhitespaceCharacter)
        if (words.size < 2) return null

        // "Summary:" style labels describe the document, not the conversation.
        val bare = line.lowercase()
        val labels = listOf("summary", "transcript", "notes", "meeting notes", "action items", "key points")
        if (bare in labels) return null

        return line
    }

    // MARK: - Strategy 2: what kind of conversation it was

    fun kind(text: String): String? {
        val haystack = HeuristicCaptureExtractor.matchable(text.prefixCharacters(scanLimit))
        return kinds.firstOrNull { (_, keywords) ->
            keywords.any { HeuristicCaptureExtractor.containsWord(haystack, it) }
        }?.first
    }

    // MARK: - Strategy 3: whatever it opens with

    fun openingSentence(text: String, limit: Int = 52): String? {
        val sentence = HeuristicCaptureExtractor.sentences(text).firstOrNull() ?: return null

        val cleaned = sentence.trimmingScalars(" .!?").replace("\n", " ")
        val characters = cleaned.graphemes()
        if (characters.size <= 3) return null
        if (characters.size <= limit) return cleaned

        val cut = characters.take(limit)
        val lastSpace = cut.lastIndexOf(" ")
        if (lastSpace < 0) return cut.joinToString("") + "…"
        return cut.subList(0, lastSpace).joinToString("") + "…"
    }

    // MARK: - Helpers

    /** `.dateTime.month(.abbreviated).day()` — "Mar 3". */
    fun day(date: Instant, calendar: KeeprCalendar = KeeprCalendar.current()): String = calendar.monthDay(date)

    /** Title for a note that is only screenshots — there's no text to read. */
    fun imageOnlyTitle(date: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): String =
        "Notes · ${day(date, calendar)}"
}
