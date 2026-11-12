package com.aulorvy.keepr.core.services

import com.aulorvy.keepr.core.model.FollowUpDraft
import com.aulorvy.keepr.core.model.InteractionKind
import com.aulorvy.keepr.core.model.KeeprCalendar
import com.aulorvy.keepr.core.model.MemoryCategory
import com.aulorvy.keepr.core.model.MemoryDraft
import java.time.Instant
import java.util.regex.Matcher
import java.util.regex.Pattern

// Translated from Keepr/Services/CaptureExtractor.swift.

/**
 * What a raw capture becomes, before the user confirms it.
 *
 * Nothing here is written to a person's record until it's reviewed — true of
 * the on-device extractor today and of any model-backed one that replaces it.
 */
data class CaptureDraft(
    val summary: String = "",
    /** A name spotted in the text, used to preselect a person. Never trusted. */
    val suggestedName: String? = null,
    val kind: InteractionKind = InteractionKind.OTHER,
    val occurredAt: Instant = Instant.now(),
    val memories: List<MemoryDraft> = emptyList(),
    val followUp: FollowUpDraft? = null,
) {
    val hasSuggestions: Boolean get() = memories.isNotEmpty() || followUp != null
}

/** Turns free text into a draft. The one seam where AI will later plug in. */
interface CaptureExtracting {
    suspend fun extract(text: String, now: Instant = Instant.now()): CaptureDraft

    /**
     * Everything someone said about a person, split up and filed.
     *
     * A different contract from [extract]: a quick note should suggest little
     * and stay quiet when unsure, but a brain dump has to keep *everything* —
     * typing six things and getting four back, with no way to know which two
     * were dropped, is worse than no feature at all.
     */
    suspend fun facts(text: String): List<MemoryDraft>
}

/**
 * On-device extraction with nothing but the platform: sentence segmentation,
 * keyword rules and [DateDetector] (the stand-in for `NSDataDetector`) for dates.
 *
 * Deliberately conservative — it would rather suggest nothing than suggest
 * something wrong, because every suggestion costs the user a tap to dismiss.
 * No network, no API key, no model.
 */
class HeuristicCaptureExtractor : CaptureExtracting {

    override suspend fun extract(text: String, now: Instant): CaptureDraft {
        val normalized = text.trimmingWhitespacesAndNewlines().replace("\n", " ")
        if (normalized.isEmpty()) return CaptureDraft(occurredAt = now)

        val clauses = clauses(normalized)
        val actionClauses = clauses.filter { isAction(it) }
        val factClauses = clauses.filter { !isAction(it) }

        val memories = factClauses.mapNotNull { memory(it) }.reduced()

        val followUp = actionClauses.firstOrNull()?.let { action ->
            FollowUpDraft(
                title = actionTitle(action),
                dueDate = dueDate(normalized, now) ?: defaultDue(now),
                hasTime = false,
            )
        }

        return CaptureDraft(
            summary = summary(normalized),
            suggestedName = personName(normalized),
            kind = kind(normalized),
            occurredAt = now,
            memories = memories,
            followUp = followUp,
        )
    }

    /** Splits a dump into one draft per fact, keeping all of it. */
    override suspend fun facts(text: String): List<MemoryDraft> {
        val normalized = text.trimmingWhitespacesAndNewlines()
        if (normalized.isEmpty()) return emptyList()

        return dumpClauses(normalized)
            .mapNotNull { clause ->
                val content = cleanedFact(clause)
                if (content.characterCount <= 2) null else MemoryDraft(content = content, category = category(clause))
            }
            // A generous cap rather than the quick-note one: a dump is meant to be
            // long, and silently truncating someone's memory of a meeting is
            // exactly the failure this feature exists to prevent.
            .reduced(limit = 40)
    }

    companion object {

        // MARK: - Summary

        fun summary(text: String, limit: Int = 140): String {
            val first = sentences(text).firstOrNull() ?: text
            val characters = first.graphemes()
            if (characters.size <= limit) return first
            val cut = characters.take(limit)
            val lastSpace = cut.lastIndexOf(" ")
            if (lastSpace < 0) return cut.joinToString("") + "…"
            return cut.subList(0, lastSpace).joinToString("") + "…"
        }

        // MARK: - Segmentation

        fun sentences(text: String): List<String> {
            val result = sentenceSubstrings(text)
                .map { it.trimmingWhitespacesAndNewlines() }
                .filter { it.isNotEmpty() }
            return result.ifEmpty { listOf(text) }
        }

        /**
         * Sentences split further on commas and "and", because one sentence
         * usually carries several separate facts.
         */
        fun clauses(text: String): List<String> =
            sentences(text)
                .flatMap { sentence ->
                    sentence
                        .replace(", and ", "|")
                        .replace(" and ", "|")
                        .replace(", ", "|")
                        .splitOmittingEmpty("|")
                }
                .map { it.trimmingScalars(" .!?") }
                .filter { it.characterCount > 3 }

        /**
         * Splitting for spoken input.
         *
         * Dictation arrives with almost no punctuation — "he owns a roofing
         * company he's got two kids wants to lose weight before the wedding" —
         * so spoken joins count as breaks too, or the whole dump lands as one
         * paragraph. Each pair is what to look for and what to leave behind: the
         * pronoun has to survive the split, or "…company and he's got two kids"
         * leaves "'s got two kids".
         */
        val spokenBreaks: List<Pair<String, String>> = listOf(
            " and then " to "|",
            " and also " to "|",
            " but also " to "|",
            " oh and " to "|",
            " also " to "|",
            " plus " to "|",
            " and he " to "|he ",
            " and she " to "|she ",
            " and they " to "|they ",
            " and we " to "|we ",
            " and his " to "|his ",
            " and her " to "|her ",
            " and their " to "|their ",
            " and i " to "|i ",
            // Spoken English contracts almost every one of these, so the plain
            // forms above would miss "and he's got two kids" entirely.
            " and he's " to "|he's ",
            " and she's " to "|she's ",
            " and they're " to "|they're ",
            " and they've " to "|they've ",
            " and we're " to "|we're ",
            " and i'm " to "|i'm ",
            " and it's " to "|it's ",
            " he said " to "|he said ",
            " she said " to "|she said ",
            " they said " to "|they said ",
            " apparently " to "|apparently ",
            " turns out " to "|turns out ",
        )

        private val spokenBreakPatterns: List<Pair<Pattern, String>> = spokenBreaks.map { (find, replace) ->
            Pattern.compile(find, Pattern.LITERAL or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) to
                Matcher.quoteReplacement(replace)
        }

        fun dumpClauses(text: String): List<String> =
            // Newlines are the strongest signal there is: a list typed on separate
            // lines means separate facts, whatever the punctuation says.
            text
                .splitCharacters(::isNewlineCharacter)
                .flatMap { line ->
                    var working = line
                    for ((pattern, replacement) in spokenBreakPatterns) {
                        working = pattern.matcher(working).replaceAll(replacement)
                    }
                    working.splitOmittingEmpty("|").flatMap { sentences(it) }
                }
                .flatMap { sentence ->
                    // Long sentences still get split; short ones are left whole,
                    // since "two kids, 7 and 10" is one fact, not three.
                    if (sentence.characterCount <= 60) {
                        listOf(sentence)
                    } else {
                        sentence
                            .replace(", and ", "|")
                            .replace(";", "|")
                            .splitOmittingEmpty("|")
                    }
                }
                .map { it.trimmingScalars(" .!?,") }
                .filter { it.characterCount > 2 }

        /** The category a clause belongs to, or [MemoryCategory.OTHER] when nothing matches. */
        fun category(clause: String): MemoryCategory {
            val haystack = matchable(clause)
            return categoryKeywords
                .firstOrNull { (_, keywords) -> keywords.any { containsWord(haystack, it) } }
                ?.first ?: MemoryCategory.OTHER
        }

        // MARK: - Keyword matching

        /**
         * Lowercases, replaces punctuation with spaces and pads the result, so
         * keywords match on word boundaries. Without this, "met" matches inside
         * "something" and every note becomes an in-person meeting.
         */
        fun matchable(text: String): String {
            val lowered = text.lowercase()
            val cleaned = lowered.graphemes().joinToString("") {
                if (isLetterCharacter(it) || isNumberCharacter(it) || it == "'") it else " "
            }
            return " " + cleaned.splitOmittingEmpty(" ").joinToString(" ") + " "
        }

        /** [haystack] must already be [matchable]. */
        fun containsWord(haystack: String, keyword: String): Boolean =
            haystack.canonical().contains((" " + keyword.trimmingWhitespaces() + " ").canonical())

        // MARK: - Interaction kind

        /**
         * Order matters: the first list to match wins, so the specific kinds sit
         * above the general ones. "Grabbed lunch" is a meal before it's in
         * person, and a Zoom is a video call before it's a meeting.
         */
        private val kindKeywords: List<Pair<InteractionKind, List<String>>> = listOf(
            InteractionKind.VIDEO to listOf("zoom", "facetime", "video call", "google meet", "on video"),
            InteractionKind.CALL to listOf("called", "phone call", "on the phone", "rang", "hopped on a call"),
            InteractionKind.DM to listOf("dm'd", "dmed", "dm from", "instagram", "linkedin message", "slack"),
            InteractionKind.TEXT to listOf("texted", "text from", "messaged"),
            InteractionKind.EMAIL to listOf("emailed", "sent an email", "email from", "replied to his email"),
            InteractionKind.MEAL to listOf("lunch", "coffee", "dinner", "breakfast", "drinks", "grabbed a bite"),
            InteractionKind.EVENT to listOf("conference", "networking event", "seminar", "expo", "workshop", "party"),
            InteractionKind.MEETING to listOf("meeting", "sat down with", "presentation"),
            InteractionKind.IN_PERSON to listOf("ran into", "met", "saw", "bumped into", "in person"),
        )

        fun kind(text: String): InteractionKind {
            val haystack = matchable(text)
            for ((kind, keywords) in kindKeywords) {
                if (keywords.any { containsWord(haystack, it) }) return kind
            }
            return InteractionKind.OTHER
        }

        // MARK: - Person name

        private val nameTriggers: Set<String> = setOf(
            "talked", "spoke", "met", "called", "texted", "emailed", "saw",
            "ran", "bumped", "caught", "messaged", "lunch", "coffee", "dinner", "with",
        )
        private val nameConnectors: Set<String> = setOf("to", "with", "up", "into", "from")
        private val nameStopWords: Set<String> = setOf(
            "I", "The", "A", "An", "At", "In", "On", "My", "His", "Her", "Their", "He", "She", "They",
        )

        /**
         * Looks for a capitalized name right after a conversational verb.
         * Returns null rather than guessing when nothing looks like a name.
         */
        fun personName(text: String): String? {
            val tokens = text.splitOmittingEmpty(" ")
            val triggerIndex = tokens.indexOfFirst { nameTriggers.contains(it.lowercase().trimmingPunctuation()) }
            if (triggerIndex < 0) return null

            var index = triggerIndex + 1
            while (index < tokens.size && nameConnectors.contains(tokens[index].lowercase())) {
                index += 1
            }

            val parts = mutableListOf<String>()
            while (index < tokens.size && parts.size < 2) {
                val token = tokens[index].trimmingPunctuation()
                val first = token.graphemes().firstOrNull() ?: break
                if (!isUppercaseCharacter(first) || nameStopWords.contains(token)) break
                parts += token
                index += 1
            }

            return if (parts.isEmpty()) null else parts.joinToString(" ")
        }

        // MARK: - Facts

        /**
         * Order is the whole design: the first list to match wins, so the narrow
         * shelves sit above the broad ones. "Could introduce me to someone at his
         * company" is an opportunity before it's a work fact, and "lost 20
         * pounds" is progress while "wants to lose 20 pounds" is still a goal —
         * which is why one list has "lost" and the other "lose".
         *
         * Keywords match whole words, so short common fragments are avoided:
         * bare "back" would catch "get back to him", "free" would catch "free
         * weights". The multi-word forms cost nothing and don't misfire.
         */
        private val categoryKeywords: List<Pair<MemoryCategory, List<String>>> = listOf(
            MemoryCategory.GOALS to listOf(
                "wants to", "trying to", "training for", "hoping to", "plans to", "goal",
                "lose", "aiming", "signed up for", "working toward", "by the end of",
                "wants me to", "would like to", "dreams of",
            ),
            MemoryCategory.IMPORTANT_DATE to listOf(
                "birthday", "anniversary", "wedding", "vacation", "trip to",
                "getting married", "due date", "retiring", "graduation",
            ),
            MemoryCategory.HEALTH to listOf(
                "injury", "injured", "surgery", "allergic", "allergy", "knee", "knees",
                "shoulder", "hip", "wrist", "ankle", "elbow", "rotator cuff", "acl",
                "herniated", "bad back", "lower back", "back pain", "back issues",
                "bulging disc", "sciatica", "arthritis", "tendinitis", "plantar",
                "diabetic", "diabetes", "asthma", "blood pressure", "cholesterol",
                "thyroid", "autoimmune", "medication", "meds", "physical therapy",
                "concussion", "recovering", "pregnant", "sleep apnea", "insomnia",
                "chronic", "flare up", "cleared by", "cortisone",
            ),
            MemoryCategory.PROGRESS to listOf(
                "lost", "dropped", "down to", "hit", "benched", "squatted", "deadlifted",
                "pr", "personal best", "ran a", "finished", "completed", "milestone",
                "so far", "since we started", "improved", "gained", "stuck to",
                "showed up every", "streak", "first time",
            ),
            MemoryCategory.FAMILY to listOf(
                "wife", "husband", "partner", "fiancee", "fiance", "kids", "children",
                "daughter", "daughters", "son", "sons", "married", "divorced", "baby",
                "grandkids", "grandchildren", "mom", "dad", "mother", "father",
                "brother", "sister", "stepson", "stepdaughter", "twins", "expecting",
            ),
            MemoryCategory.SCHEDULE to listOf(
                "trains", "works out", "schedule", "mornings", "evenings", "afternoons",
                "weekends", "weekdays", "travels", "traveling", "on the road", "shift",
                "night shift", "time zone", "busy season", "off on", "early riser",
                "night owl", "only available", "lunch break", "picks up the kids",
            ),
            MemoryCategory.OPPORTUNITY to listOf(
                "could introduce", "can introduce", "knows a", "knows someone",
                "knows the", "looking for", "in the market", "might need", "asked about",
                "referral", "referred", "refer", "connect me", "introduce me",
                "interested in working", "wants a quote", "investor", "owns the building",
                "sits on the board", "warm lead", "worth pitching", "good fit for",
            ),
            MemoryCategory.WORK to listOf(
                "owns", "owner", "runs a", "works at", "works for", "founded", "company",
                "business", "job", "hiring", "ceo", "manager", "self employed",
                "contractor", "franchise", "employees", "crew", "shop", "practice",
                "got promoted", "new role", "laid off", "retired from",
            ),
            MemoryCategory.INTERESTS to listOf(
                "loves", "enjoys", "plays", "fan of", "collects", "hobby", "runs the",
                "obsessed with", "watches", "golfs", "hunts", "fishes",
                "season tickets", "band", "church",
            ),
            MemoryCategory.STYLE to listOf(
                "blunt", "direct", "chatty", "talkative", "quiet", "reserved", "guarded",
                "responds", "never answers", "no small talk", "straight to the point",
                "competitive", "skeptical", "easygoing", "intense", "needs reassurance",
                "overthinks", "checks in", "hates being sold", "takes a while to",
            ),
            MemoryCategory.PREFERENCES to listOf(
                "prefers", "hates", "doesn't like", "likes his", "likes her",
                "vegetarian", "vegan", "pescatarian", "gluten", "dairy", "black coffee",
                "won't", "always orders", "can't stand", "picky",
            ),
            MemoryCategory.PERSONAL to listOf(
                "lives in", "moved to", "grew up", "originally from", "house", "apartment",
                "bought a", "renting", "commutes", "military", "veteran", "went to school",
                "studied", "immigrated", "dog", "cat", "pets",
            ),
            MemoryCategory.BUSINESS to listOf(
                "client", "pricing", "proposal", "contract", "invoice", "budget",
                "package", "pays", "paid", "renewal", "cancelled", "discount",
                "month to month", "deposit", "card on file",
            ),
        )

        /** Phrases that mean "this clause states something about the person". */
        private val factMarkers: List<String> = categoryKeywords.flatMap { it.second } + listOf(
            "has", "is a", "is the", "just", "started", "used to",
        )

        fun memory(clause: String): MemoryDraft? {
            val haystack = matchable(clause)
            if (factMarkers.none { containsWord(haystack, it) }) return null

            val content = cleanedFact(clause)
            if (content.characterCount <= 5) return null

            val category = categoryKeywords
                .firstOrNull { (_, keywords) -> keywords.any { containsWord(haystack, it) } }
                ?.first ?: MemoryCategory.OTHER

            return MemoryDraft(content = content, category = category)
        }

        /**
         * Drops the leading pronoun or connector and capitalizes, so "and wants
         * to lose 20 pounds" reads "Wants to lose 20 pounds".
         */
        fun cleanedFact(clause: String): String {
            var text = clause.trimmingWhitespaces()
            for (prefix in listOf("he ", "she ", "they ", "and ", "also ", "but ", "who ", "that ")) {
                if (text.lowercase().startsWith(prefix)) {
                    text = text.dropFirstCharacters(prefix.length)
                    break
                }
            }
            val characters = text.graphemes()
            val first = characters.firstOrNull() ?: return text
            return first.uppercase() + characters.drop(1).joinToString("")
        }

        // MARK: - Actions

        private val actionMarkers: List<String> = listOf(
            "follow up", "text him", "text her", "text them", "text me", "call him", "call her",
            "call them", "email him", "email her", "send him", "send her", "send them",
            "reach out", "check in", "circle back", "get back to", "ask him", "ask her",
            "remind me", "need to", "should", "have to", "supposed to", "said to",
        )

        fun isAction(clause: String): Boolean {
            val haystack = matchable(clause)
            return actionMarkers.any { containsWord(haystack, it) }
        }

        /** "said to text him next week about training" → "Text him next week about training" */
        fun actionTitle(clause: String): String {
            var text = clause.trimmingWhitespaces()
            for (prefix in listOf(
                "said to ", "he said to ", "she said to ", "need to ", "i need to ",
                "i should ", "should ", "have to ", "i have to ", "remind me to ",
                "supposed to ", "i'm supposed to ",
            )) {
                if (text.lowercase().startsWith(prefix)) {
                    text = text.dropFirstCharacters(prefix.length)
                    break
                }
            }
            text = cleanedFact(text)
            return text.ifEmpty { "Follow up" }
        }

        // MARK: - Dates

        private val relativeDays: List<Pair<String, Int>> = listOf(
            "day after tomorrow" to 2,
            "tomorrow" to 1,
            "next week" to 7,
            "in a week" to 7,
            "next month" to 30,
            "in a few days" to 3,
            "in a couple days" to 2,
            "this weekend" to 5,
            "next weekend" to 12,
        )

        /**
         * Prefers a small table of relative phrases, then an explicit date found
         * by [DateDetector]. Returns null when neither is present, so the caller
         * picks its own default rather than inventing a deadline.
         *
         * `NSDataDetector` resolves against the wall clock; [DateDetector] is
         * given [now] instead, which is the same thing in the app and makes this
         * deterministic under test.
         */
        fun dueDate(text: String, now: Instant, calendar: KeeprCalendar = KeeprCalendar.current()): Instant? {
            val haystack = matchable(text)

            relativeDays.firstOrNull { containsWord(haystack, it.first) }?.let { match ->
                val start = calendar.startOfDay(now)
                return calendar.addingDays(match.second, start)
            }

            val detected = DateDetector.matches(text, now, calendar)
                .map { it.date }
                .filter { it > now }
                .minOrNull() ?: return null
            return calendar.startOfDay(detected)
        }

        fun defaultDue(now: Instant, calendar: KeeprCalendar = KeeprCalendar.current()): Instant {
            val start = calendar.startOfDay(now)
            return calendar.addingDays(3, start)
        }
    }
}

// MARK: - Draft cleanup

/** Drops near-duplicates and caps how many suggestions the user has to triage. */
private fun List<MemoryDraft>.reduced(limit: Int = 5): List<MemoryDraft> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<MemoryDraft>()
    for (draft in this) {
        val key = draft.content.lowercase().canonical()
        if (key in seen) continue
        seen += key
        result += draft
        if (result.size == limit) break
    }
    return result
}

/** Returns nothing. Used by previews that want the plain manual flow. */
class NoopCaptureExtractor : CaptureExtracting {
    override suspend fun extract(text: String, now: Instant): CaptureDraft = CaptureDraft(summary = text, occurredAt = now)

    override suspend fun facts(text: String): List<MemoryDraft> = emptyList()
}
