package com.aulorvy.keepr.core.domain

import com.aulorvy.keepr.core.model.PersonGroup
import com.aulorvy.keepr.core.model.RelationshipTag
import com.aulorvy.keepr.core.model.TagKind
import com.aulorvy.keepr.core.services.ContactSummary

// Translated from Keepr/Domain/ContactMarkers.swift.

/**
 * The things this user calls their groups and relationship types, flattened
 * into something a parser can match text against.
 *
 * Built from the user's own records rather than a fixed list, because the
 * shorthand on a contact card is personal: "LT Client" only means Life Time if
 * this user says LT means Life Time.
 */
data class MarkerVocabulary(
    var groups: List<Entry> = emptyList(),
    var types: List<Entry> = emptyList(),
) {

    data class Entry(
        /** A group's UUID string (uppercase, like `uuidString`), or a tag's name. */
        val id: String,
        /** What to show the user: "Life Time". */
        val display: String,
        /** Lowercased things that mean this entry, longest first. */
        val terms: List<String>,
        /** Which side a type sits on. Null for groups, which span both. */
        var kind: TagKind? = null,
    )

    val isEmpty: Boolean get() = groups.isEmpty() && types.isEmpty()

    companion object {

        fun build(groups: List<PersonGroup>, tags: List<RelationshipTag>): MarkerVocabulary =
            MarkerVocabulary(
                groups = groups.map { group ->
                    Entry(
                        id = group.id.toString().uppercase(),
                        display = group.name,
                        terms = terms(group.name, group.aliases),
                    )
                },
                types = tags.map { tag ->
                    Entry(
                        id = tag.name,
                        display = tag.name,
                        terms = terms(
                            tag.name,
                            tag.aliases,
                            extra = builtInSynonyms[tag.builtInKey ?: ""] ?: emptyList(),
                        ),
                        kind = tag.kind,
                    )
                },
            )

        /**
         * Everything that should count as naming this thing: its own name, the
         * initials of a multi-word name ("Life Time" → "LT"), and anything the
         * user typed into the aliases field.
         */
        fun terms(name: String, aliases: String?, extra: List<String> = emptyList()): List<String> {
            val result = mutableSetOf<String>()

            val trimmed = name.trimmingWhitespacesAndNewlines().lowercase()
            if (trimmed.isNotEmpty()) {
                result += trimmed
                // "Life Time" also matches "lifetime" — the space is a coin flip.
                val collapsed = trimmed.replace(" ", "")
                if (collapsed != trimmed) result += collapsed

                val words = trimmed.splitCharacters { it == " " || it == "-" }
                if (words.size > 1) {
                    val initials = words.joinToString("") { it.graphemes().first() }
                    if (initials.characterCount >= 2) result += initials
                }
            }

            for (alias in (aliases ?: "").splitCharacters { it == "," || it == ";" }) {
                val value = alias.trimmingWhitespacesAndNewlines().lowercase()
                if (value.isNotEmpty()) result += value
            }
            for (value in extra) result += value.lowercase()

            // Longest first so "current client" wins over "client".
            return result.sortedWith { a, b ->
                val ca = a.characterCount
                val cb = b.characterCount
                if (ca == cb) swiftCompare(a, b) else cb.compareTo(ca)
            }
        }

        /**
         * Words people actually write on a card, mapped to the built-in they
         * mean. Keyed by `builtInKey`, so a renamed built-in still picks them up.
         */
        val builtInSynonyms: Map<String, List<String>> = mapOf(
            "Current Client" to listOf("client", "clients", "training client", "pt client"),
            "Potential Client" to listOf("prospect", "potential"),
            "Lead" to listOf("lead", "inquiry"),
            "Vendor" to listOf("vendor", "supplier"),
            "Team" to listOf("staff", "coach", "trainer"),
            "Business Partner" to listOf("partner"),
            "Referral Source" to listOf("referral", "referrer"),
            "Investor" to listOf("investor"),
            "Professional Contact" to listOf("work", "business"),
            "Family" to listOf("family", "fam"),
            "Close Friend" to listOf("bestie"),
            "Friend" to listOf("friend"),
            "Acquaintance" to listOf("acquaintance"),
        )
    }
}

/** What was found on one contact card. */
data class MarkerParse(
    /** Group entry ids (UUID strings), in the order found. */
    var groupIDs: List<String> = emptyList(),
    var groupNames: List<String> = emptyList(),
    /** Relationship type names. */
    var typeNames: List<String> = emptyList(),
    /** The name with recognized markers taken out. Empty means the field held nothing but markers. */
    var givenName: String = "",
    var familyName: String = "",
    /** Marker text removed from the name fields, for showing and for the record. */
    var removedFromName: List<String> = emptyList(),
    /** Plain-language evidence, shown under the contact so a guess can be judged. */
    var reasons: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = groupIDs.isEmpty() && typeNames.isEmpty()
    val changedName: Boolean get() = removedFromName.isNotEmpty()
}

/**
 * Reads the shorthand people put in contact fields — "Stanley LT Client",
 * "Dana (HYP)", "Mike - Bumble" — and turns it into a category, a group, and a
 * clean name.
 *
 * Two rules keep this from doing damage:
 * - **Only whole words, and only words the user's own vocabulary knows.**
 * - **Short markers must be shouted.** "LT" matches in "LT Client" but not in
 *   "Lt Cmdr Data", because a term of four Characters or fewer only counts
 *   when the card wrote it in capitals. Longer terms match any case.
 *
 * Ranges are half-open UTF-16 offsets into the field, as [IntRange]s built
 * with `until`.
 */
object ContactMarkerParser {

    /** At or below this length, a marker has to be ALL CAPS on the card to count. */
    private const val SHOUTING_THRESHOLD = 4

    private data class Field(val value: String, val label: String, val cleans: Boolean)

    fun parse(contact: ContactSummary, vocabulary: MarkerVocabulary): MarkerParse {
        val parse = MarkerParse(givenName = contact.givenName, familyName = contact.familyName)
        if (vocabulary.isEmpty) return parse

        // Name fields are the only ones cleaned up. A marker in the company
        // field is still evidence, but "Life Time" as an employer is the truth
        // about where someone works and shouldn't be edited out.
        val fields = mutableListOf(
            Field(contact.givenName, "first name", true),
            Field(contact.familyName, "last name", true),
        )
        contact.organizationName?.let { fields += Field(it, "company", false) }
        contact.jobTitle?.let { fields += Field(it, "job title", false) }

        val seenGroups = mutableSetOf<String>()
        val seenTypes = mutableSetOf<String>()

        fun record(entry: MarkerVocabulary.Entry, isGroup: Boolean, field: String) {
            if (isGroup) {
                if (!seenGroups.add(entry.id)) return
                parse.groupIDs += entry.id
                parse.groupNames += entry.display
            } else {
                if (!seenTypes.add(entry.id)) return
                parse.typeNames += entry.display
            }
            parse.reasons += "\"${entry.display}\" from the $field field"
        }

        for (field in fields) {
            val found = matches(field.value, vocabulary)
            if (found.isEmpty()) continue

            for (match in found) record(match.entry, match.isGroup, field.label)

            if (!field.cleans) continue
            val cleaned = removing(found.map { it.range }, field.value)
            if (field.label == "first name") parse.givenName = cleaned else parse.familyName = cleaned
            parse.removedFromName += found.map { field.value.substring(it.range.first, it.range.last + 1) }
        }

        // Never leave someone nameless. If the markers ate the whole card,
        // categorize but keep the name exactly as it was.
        if (parse.givenName.isEmpty() && parse.familyName.isEmpty()) {
            parse.givenName = contact.givenName
            parse.familyName = contact.familyName
            parse.removedFromName = emptyList()
        }

        return parse
    }

    // MARK: Matching

    private class Match(val entry: MarkerVocabulary.Entry, val isGroup: Boolean, val range: IntRange)

    private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

    /** Non-overlapping matches in one field, groups and types alike. */
    private fun matches(value: String, vocabulary: MarkerVocabulary): List<Match> {
        val candidates = vocabulary.groups.map { it to true } + vocabulary.types.map { it to false }
        val results = mutableListOf<Match>()
        val claimed = mutableListOf<IntRange>()

        for ((entry, isGroup) in candidates) {
            for (term in entry.terms) {
                val range = range(term, value) ?: continue
                if (claimed.any { it.overlaps(range) }) continue
                claimed += range
                results += Match(entry, isGroup, range)
                break
            }
        }
        return results.sortedBy { it.range.first }
    }

    /**
     * Finds [term] as a whole word (no letter or number Character either side).
     * Short terms additionally have to be capitalized on the card.
     */
    fun range(term: String, value: String): IntRange? {
        if (term.isEmpty()) return null
        val bounds = value.graphemeBoundaries()
        var searchStart = 0

        while (searchStart < value.length) {
            val start = value.indexOf(term, searchStart, ignoreCase = true)
            if (start < 0) break
            val end = start + term.length

            val before = if (start == 0) null else characterBefore(value, bounds, start)
            val after = if (end == value.length) null else characterAt(value, bounds, end)

            val isWhole = !(before?.let(::isWordCharacter) ?: false) &&
                !(after?.let(::isWordCharacter) ?: false)
            val text = value.substring(start, end)
            val isShouted = term.characterCount > SHOUTING_THRESHOLD || text == text.uppercase()

            if (isWhole && isShouted) return start until end
            searchStart = end
        }
        return null
    }

    /** The Character just before [index]. */
    private fun characterBefore(value: String, bounds: IntArray, index: Int): String {
        val startOfPrevious = bounds.last { it < index }
        val endOfPrevious = bounds.first { it > startOfPrevious }
        return value.substring(startOfPrevious, endOfPrevious)
    }

    /** The Character starting at (or containing) [index]. */
    private fun characterAt(value: String, bounds: IntArray, index: Int): String {
        val start = bounds.last { it <= index }
        val end = bounds.first { it > start }
        return value.substring(start, end)
    }

    private fun isWordCharacter(character: String): Boolean =
        isSwiftLetter(character) || isSwiftNumber(character)

    // MARK: Discovery

    /** A shorthand that keeps appearing on contact cards but means nothing to Keepr yet. */
    data class Candidate(val token: String, val count: Int) {
        val id: String get() = token
    }

    /**
     * Finds the shorthand this user already uses, so the first import can offer
     * "LT — on 8 contacts. Make it a group?".
     *
     * Only two shapes count, because both are unambiguously a label rather than
     * part of a name: SHOUTED short tokens, and anything in brackets.
     */
    fun candidates(
        contacts: List<ContactSummary>,
        vocabulary: MarkerVocabulary,
        minimum: Int = 2,
        limit: Int = 6,
    ): List<Candidate> {
        val known = (vocabulary.groups + vocabulary.types).flatMap { it.terms }.toSet()
        val counts = mutableMapOf<String, Int>()
        val display = mutableMapOf<String, String>()

        for (contact in contacts) {
            val seen = mutableSetOf<String>()
            for (value in listOf(contact.givenName, contact.familyName)) {
                for (token in labelTokens(value)) {
                    val key = token.lowercase()
                    if (key in known || !seen.add(key)) continue
                    counts[key] = (counts[key] ?: 0) + 1
                    display[key] = display[key] ?: token
                }
            }
        }

        return counts.entries
            .filter { it.value >= minimum }
            .sortedWith { a, b ->
                if (a.value == b.value) swiftCompare(a.key, b.key) else b.value.compareTo(a.value)
            }
            .take(limit)
            .mapNotNull { (key, count) -> display[key]?.let { Candidate(it, count) } }
    }

    /** Tokens in a name field that read as a label rather than a name. */
    fun labelTokens(value: String): List<String> {
        val results = mutableListOf<String>()

        // Anything in brackets: "Dana (HYP)", "Mike [Bumble]".
        val buffer = StringBuilder()
        var depth = 0
        for (character in value.graphemes()) {
            if (character == "(" || character == "[") {
                depth += 1
                buffer.clear()
            } else if (character == ")" || character == "]") {
                if (depth > 0) {
                    val inner = buffer.toString().trimmingWhitespacesAndNewlines()
                    if (inner.isNotEmpty() && inner.splitCharacters(::isSwiftWhitespace).size <= 2) {
                        results += inner
                    }
                }
                depth = maxOf(0, depth - 1)
                buffer.clear()
            } else if (depth > 0) {
                buffer.append(character)
            }
        }

        // SHOUTED short tokens: "LT", "HYP", "MP".
        for (text in value.splitCharacters { !isSwiftLetter(it) && !isSwiftNumber(it) }) {
            val count = text.characterCount
            if (count < 2 || count > 5 || text != text.uppercase() || text.graphemes().none(::isSwiftLetter)) continue
            results += text
        }

        return results
    }

    /**
     * Punctuation a marker tends to be wrapped in — "Dana (HYP)", "Mike - Bumble".
     * Left behind, it looks like the app made a mess of someone's name.
     */
    private val wrappers: Set<String> =
        setOf("(", ")", "[", "]", "{", "}", "|", "/", "\\", "-", "–", "—", ":", ",", "•")

    /** Takes the matched ranges out and tidies up what's left. */
    fun removing(ranges: List<IntRange>, value: String): String {
        val result = StringBuilder(value)
        for (range in ranges.sortedByDescending { it.first }) {
            result.replace(range.first, range.last + 1, " ")
        }
        return result.toString().graphemes()
            .joinToString("") { if (it in wrappers) " " else it }
            .splitCharacters(::isSwiftWhitespace)
            .joinToString(" ")
    }
}
