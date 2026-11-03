package com.chrisallenclark.keepr.core.domain

import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.foldedContains
import com.chrisallenclark.keepr.core.model.trimmed
import java.text.Normalizer
import java.util.UUID

// Translated from Keepr/Domain/SearchEngine.swift.

/** Where a search query matched. Ranked in declaration order (enum order is [rawValue] order). */
enum class SearchField(val rawValue: Int, val label: String, val groupTitle: String, val symbolName: String) {
    NAME(0, "Name", "People", "person"),
    COMPANY(1, "Company", "People", "building.2"),
    TAG(2, "Tag", "People", "tag"),
    MEMORY(3, "Memory", "Memories", "lightbulb"),
    WRITE_UP(4, "Note", "Notes", "doc.text"),
    INTERACTION(5, "Interaction", "Interactions", "bubble.left.and.bubble.right"),
    FILE(6, "File", "Files", "paperclip"),
    NOTE(7, "Profile", "Details", "note.text"),
    FOLLOW_UP(8, "Follow-up", "Follow-ups", "bell"),
}

/** One person, plus why they matched. */
data class SearchResult(
    val person: Person,
    val field: SearchField,
    /** The line of text that matched, for the result's second line. */
    val snippet: String?,
) {
    val id: UUID get() = person.id
}

/** Results that matched for the same reason, shown as one section. */
data class SearchSection(val field: SearchField, val results: List<SearchResult>) {
    val id: Int get() = this.field.rawValue
    val title: String get() = this.field.groupTitle
}

/**
 * Literal, on-device search across everything the user has recorded.
 *
 * Deliberately simple: one query, scanned over pre-joined text. The shape —
 * score each person, return the best reason they matched — is what a future
 * embedding-based ranker would slot into, so callers won't change.
 */
object SearchEngine {

    /**
     * Search ignores the Business/Personal switch: when you're looking for
     * someone, you're looking for *someone*.
     */
    fun search(query: String, people: List<Person>, limit: Int = 60): List<SearchResult> {
        val trimmed = query.trimmed()
        if (trimmed.isEmpty()) return emptyList()

        return people
            .mapNotNull { bestMatch(it, trimmed) }
            .sortedWith { lhs, rhs ->
                if (lhs.field != rhs.field) lhs.field.compareTo(rhs.field)
                else swiftCompare(lhs.person.sortKey, rhs.person.sortKey)
            }
            .take(limit)
    }

    /** The highest-ranked field this person matched on, if any. */
    fun bestMatch(person: Person, query: String): SearchResult? {
        fun contains(text: String?): Boolean {
            if (text.isNullOrEmpty()) return false
            return text.localizedStandardContains(query)
        }

        if (contains(person.fullName) || contains(person.preferredName)) {
            return SearchResult(person, SearchField.NAME, person.subtitle)
        }
        if (contains(person.company) || contains(person.jobTitle)) {
            return SearchResult(person, SearchField.COMPANY, person.subtitle)
        }
        // What they do is searchable — most of the reason the field exists.
        if (contains(person.workNote)) {
            return SearchResult(person, SearchField.COMPANY, person.workNote)
        }
        person.groupList.firstOrNull { contains(it.name) }?.let {
            return SearchResult(person, SearchField.TAG, it.name)
        }
        person.tagList.firstOrNull { contains(it.name) }?.let {
            return SearchResult(person, SearchField.TAG, it.name)
        }
        person.memoryList.firstOrNull { contains(it.content) }?.let {
            return SearchResult(person, SearchField.MEMORY, it.content)
        }
        // Pasted write-ups are long, so they're the likeliest home of a
        // half-remembered phrase. The snippet is the note's title: a line
        // pulled from the middle of a transcript reads as nonsense without it.
        person.conversationNotes.firstOrNull { contains(it.title) || contains(it.body) }?.let {
            return SearchResult(person, SearchField.WRITE_UP, it.title)
        }
        person.interactionList.firstOrNull { contains(it.searchableText) }?.let {
            return SearchResult(person, SearchField.INTERACTION, it.headline)
        }
        // Files are searchable by what you named them — every file, including
        // images that sit inside a conversation note.
        person.files.firstOrNull { contains(it.title) || contains(it.note) }?.let {
            return SearchResult(person, SearchField.FILE, it.title)
        }
        if (contains(person.notes) || contains(person.howWeMet) || contains(person.introducedBy)) {
            val snippet = if (contains(person.notes)) person.notes else person.howWeMet
            return SearchResult(person, SearchField.NOTE, snippet)
        }
        person.followUpList.firstOrNull { contains(it.title) || contains(it.note) }?.let {
            return SearchResult(person, SearchField.FOLLOW_UP, it.title)
        }
        return null
    }

    /** Groups results by why they matched, in [SearchField] order, dropping empty groups. */
    fun grouped(results: List<SearchResult>): List<SearchSection> {
        val buckets = mutableMapOf<SearchField, MutableList<SearchResult>>()
        for (result in results) {
            buckets.getOrPut(result.field) { mutableListOf() }.add(result)
        }
        return SearchField.entries.mapNotNull { field ->
            val items = buckets[field]
            if (items.isNullOrEmpty()) null else SearchSection(field, items)
        }
    }
}

/**
 * `localizedStandardContains`: case- and diacritic-insensitive containment.
 * Foundation's version is also width-insensitive; nothing Keepr stores relies
 * on that.
 */
private fun String.localizedStandardContains(other: String): Boolean = foldedContains(other)

