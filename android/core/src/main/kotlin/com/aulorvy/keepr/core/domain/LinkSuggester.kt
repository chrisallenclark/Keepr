package com.aulorvy.keepr.core.domain

import com.aulorvy.keepr.core.model.LinkRole
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.folded
import com.aulorvy.keepr.core.services.ContactRelation
import java.util.UUID

// Translated from Keepr/Domain/LinkSuggester.swift.

/** A proposed link between two people, with the evidence for it. */
data class LinkSuggestion(
    val candidate: Person,
    val role: LinkRole,
    /** Shown under the name so the guess can be judged rather than trusted. */
    val reason: String,
) {
    val id: UUID get() = candidate.id
}

/**
 * Turns the relations already written on a contact card into proposed links
 * between people who are both already in Keepr.
 *
 * Narrow on purpose: a suggestion is only made when the name on the card
 * matches someone outright. Fuzzy-matching "Mom" against "Linda Clark" would be
 * a guess, and a wrong link between two real people is worse than none.
 */
object LinkSuggester {

    /**
     * Case- and accent-insensitive, whitespace-collapsed. "José  Ruiz" and
     * "jose ruiz" are the same person; nothing looser than that counts.
     */
    fun normalized(name: String): String {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in name.folded()) {
            if (ch.isWhitespace()) {
                if (current.isNotEmpty()) {
                    words += current.toString()
                    current.clear()
                }
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) words += current.toString()
        return words.joinToString(" ")
    }

    /**
     * True when a name off a contact card names this person. Both the stored
     * name and the user's own nickname for them count.
     */
    fun matches(person: Person, name: String): Boolean {
        val target = normalized(name)
        if (target.isEmpty()) return false
        return listOf(person.fullName, person.displayName)
            .map { normalized(it) }
            .contains(target)
    }

    /**
     * Suggestions for one person from the relations on their contact card
     * ([relations], e.g. mother → Jane Smith), among everyone in Keepr
     * ([candidates]). Never the person themselves, never someone already
     * linked, and each candidate at most once.
     */
    fun suggestions(
        relations: List<ContactRelation>,
        person: Person,
        candidates: List<Person>,
    ): List<LinkSuggestion> {
        val used = mutableSetOf(person.id)
        val results = mutableListOf<LinkSuggestion>()

        for (relation in relations) {
            val match = candidates.firstOrNull { it.id !in used && matches(it, relation.name) } ?: continue
            if (person.isLinked(match)) continue

            used.add(match.id)
            results.add(
                LinkSuggestion(
                    candidate = match,
                    role = LinkRole.forContactLabel(relation.label),
                    reason = "Listed as ${relation.label.lowercase()} on their contact card",
                ),
            )
        }
        return results
    }
}
