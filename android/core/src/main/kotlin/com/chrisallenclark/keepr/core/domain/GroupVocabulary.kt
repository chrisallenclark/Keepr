package com.chrisallenclark.keepr.core.domain

import com.chrisallenclark.keepr.core.model.localizedCaseInsensitiveCompare
import com.chrisallenclark.keepr.core.model.trimmed
import java.text.BreakIterator

// Translated from Keepr/DesignSystem/GroupVocabulary.swift (pure logic, so it lives in domain).

/**
 * What this user calls the second axis.
 *
 * The concept is fixed — a named list a person can belong to more than one of
 * — but the word isn't: one user's are businesses, another's apps, another's
 * places. Making the noun a setting costs one stored string and stops the app
 * arguing with everyone about a word.
 */
data class GroupVocabulary(val singular: String, val plural: String) {
    val id: String get() = singular

    companion object {
        val default = GroupVocabulary("Group", "Groups")

        val presets: List<GroupVocabulary> = listOf(
            default,
            GroupVocabulary("Business", "Businesses"),
            GroupVocabulary("Place", "Places"),
            GroupVocabulary("App", "Apps"),
            GroupVocabulary("Circle", "Circles"),
            GroupVocabulary("Source", "Sources"),
        )

        /** "a" or "an", so "Create a Business" and "Create an App" both read right. */
        fun article(singular: String): String {
            val first = graphemes(singular.trimmed().lowercase()).firstOrNull() ?: "z"
            return if (first in setOf("a", "e", "i", "o", "u")) "an" else "a"
        }

        /** The plural for a stored singular, falling back to a naive "+s" for a word the user typed. */
        fun plural(singular: String): String {
            presets.firstOrNull { localizedCaseInsensitiveCompare(it.singular, singular) == 0 }?.let {
                return it.plural
            }
            val trimmed = singular.trimmed()
            if (trimmed.isEmpty()) return default.plural
            if (trimmed.lowercase().endsWith("s")) return trimmed
            val characters = graphemes(trimmed)
            if (trimmed.lowercase().endsWith("y") && characters.size > 1) {
                return characters.dropLast(1).joinToString("") + "ies"
            }
            return trimmed + "s"
        }

        /** Swift `Character`s: extended grapheme clusters. */
        private fun graphemes(text: String): List<String> {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(text)
            val out = mutableListOf<String>()
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                out += text.substring(start, end)
                start = end
                end = iterator.next()
            }
            return out
        }
    }
}
