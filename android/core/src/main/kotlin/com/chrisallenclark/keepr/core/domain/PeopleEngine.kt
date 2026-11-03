package com.chrisallenclark.keepr.core.domain

import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.model.TagKind
import com.chrisallenclark.keepr.core.model.trimmed
import java.text.BreakIterator
import java.text.Normalizer
import java.util.UUID

// Translated from Keepr/Domain/PeopleEngine.swift.

/** How the People list is ordered. */
enum class PeopleSort(val rawValue: String, val title: String, val symbolName: String) {
    RECENT("recent", "Recent", "clock"),
    NAME("name", "Name", "textformat"),
    NEEDS_FOLLOW_UP("needsFollowUp", "Needs Follow-Up", "bell"),
    PRIORITY("priority", "Priority", "star"),
    NEWEST("newest", "Newest", "sparkles");

    val id: String get() = rawValue

    companion object {
        fun from(raw: String?): PeopleSort? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** One A–Z section of the People list. */
data class PersonSection(val key: String, val people: List<Person>) {
    val id: String get() = key
}

/** One relationship-type section of a place — "Current Client", "Team". */
data class PlaceSection(val title: String, val people: List<Person>) {
    val id: String get() = title
}

/**
 * The place half of the People filter: a group, nobody's group, or no filter.
 *
 * "Unplaced" is a real answer rather than an absence — "who haven't I said
 * anything about yet?" is the question that keeps the rest of it honest.
 */
sealed interface PlaceFilter {
    data object All : PlaceFilter
    data class Group(val id: UUID) : PlaceFilter
    data object Unplaced : PlaceFilter

    /** Matches the id used by [FilterFacet], so the chip row can stay dumb. */
    val facetID: String?
        get() = when (this) {
            All -> null
            is Group -> id.uuidString()
            Unplaced -> FilterFacet.unplacedID
        }

    companion object {
        /** `PlaceFilter(facetID:)` — a stale or malformed id falls back to [All]. */
        operator fun invoke(facetID: String?): PlaceFilter = when (facetID) {
            null -> All
            FilterFacet.unplacedID -> Unplaced
            else -> parseUUID(facetID)?.let { Group(it) } ?: All
        }
    }
}

/**
 * One tappable filter chip: what it's called, and how many people are behind
 * it *given everything else that's already filtered*.
 *
 * The count is the whole point: a chip that says "Clients 5" answers the
 * question before you tap it, and a chip that would show nothing never appears.
 */
data class FilterFacet(val id: String, val name: String, val symbolName: String, val count: Int) {
    companion object {
        const val unplacedID = "keepr.unplaced"
    }
}

/**
 * Pure filtering and sorting for the People list.
 *
 * Deliberately free of store queries so every rule here is unit-testable and
 * the same helpers can serve Today, People and Search.
 */
object PeopleEngine {

    /** People visible in the given mode: matching context, not archived. */
    fun visible(people: List<Person>, mode: ContextMode): List<Person> =
        people.filter { it.status == RelationshipStatus.ACTIVE && it.context.matches(mode) }

    /** Applies the optional tag filter, place, favorites and free-text query on top of [visible]. */
    fun filter(
        people: List<Person>,
        mode: ContextMode,
        tagName: String? = null,
        query: String = "",
        favoritesOnly: Boolean = false,
        place: PlaceFilter = PlaceFilter.All,
    ): List<Person> {
        var result = visible(people, mode)

        if (tagName != null) {
            result = result.filter { it.hasTag(tagName) }
        }
        when (place) {
            PlaceFilter.All -> Unit
            is PlaceFilter.Group -> result = result.filter { person -> person.groupList.any { it.id == place.id } }
            PlaceFilter.Unplaced -> result = result.filter { it.groupList.isEmpty() }
        }
        if (favoritesOnly) {
            result = result.filter { it.isFavorite }
        }

        val trimmed = query.trimmed()
        if (trimmed.isNotEmpty()) {
            result = result.filter { matches(it, trimmed) }
        }
        return result
    }

    /**
     * Whether this person matches the People search field. Delegates to
     * [SearchEngine], so typing "soccer" finds the person whose daughter plays
     * soccer — not just people with "soccer" in their name.
     */
    fun matches(person: Person, query: String): Boolean =
        SearchEngine.bestMatch(person, query) != null

    /**
     * Why this person matched, so the row can say so. Separate from [filter]:
     * the facet counts call [filter] several times per keystroke and have no
     * use for the reasons.
     */
    fun reason(person: Person, query: String): SearchResult? {
        val trimmed = query.trimmed()
        if (trimmed.isEmpty()) return null
        return SearchEngine.bestMatch(person, trimmed)
    }

    fun sort(people: List<Person>, sort: PeopleSort): List<Person> = when (sort) {
        PeopleSort.NAME -> people.sortedByLess { lhs, rhs ->
            val a = lhs.sortKey
            val b = rhs.sortKey
            if (swiftCompare(a, b) == 0) swiftCompare(lhs.displayName, rhs.displayName) < 0 else swiftCompare(a, b) < 0
        }
        // Never-contacted people sort last rather than pretending to be ancient.
        PeopleSort.RECENT -> people.sortedByLess { lhs, rhs ->
            val l = lhs.lastInteractionAt
            val r = rhs.lastInteractionAt
            when {
                l != null && r != null -> l > r
                l == null && r != null -> false
                l != null && r == null -> true
                else -> swiftCompare(lhs.sortKey, rhs.sortKey) < 0
            }
        }
        PeopleSort.NEEDS_FOLLOW_UP -> people.sortedByLess { lhs, rhs ->
            val l = lhs.nextFollowUp?.dueDate
            val r = rhs.nextFollowUp?.dueDate
            when {
                l != null && r != null -> l < r
                l == null && r != null -> false
                l != null && r == null -> true
                else -> swiftCompare(lhs.sortKey, rhs.sortKey) < 0
            }
        }
        PeopleSort.PRIORITY -> people.sortedByLess { lhs, rhs ->
            when {
                lhs.isFavorite != rhs.isFavorite -> lhs.isFavorite
                lhs.priority.weight != rhs.priority.weight -> lhs.priority.weight > rhs.priority.weight
                else -> swiftCompare(lhs.sortKey, rhs.sortKey) < 0
            }
        }
        PeopleSort.NEWEST -> people.sortedByLess { lhs, rhs -> lhs.createdAt > rhs.createdAt }
    }

    // MARK: Facets

    /**
     * Relationship-type chips for a set of people, in the tag order the user
     * sees everywhere else. Empty chips are dropped — "Investor 0" is a dead
     * end — except [keeping], the current selection, so the user is never
     * stranded on a chip that vanished under them.
     */
    fun typeFacets(
        people: List<Person>,
        tags: List<RelationshipTag>,
        mode: ContextMode,
        keeping: String? = null,
    ): List<FilterFacet> =
        tags
            .filter { it.kind == TagKind.of(mode) }
            .map { tag ->
                FilterFacet(
                    id = tag.name,
                    name = tag.name,
                    symbolName = tag.symbolName,
                    count = people.count { it.hasTag(tag.name) },
                )
            }
            .filter { it.count > 0 || it.id == keeping }

    /** Place chips, plus a "No Place" chip when there are people who aren't anywhere yet. */
    fun placeFacets(
        people: List<Person>,
        groups: List<PersonGroup>,
        mode: ContextMode,
        keeping: String? = null,
    ): List<FilterFacet> {
        val facets = groups
            .filter { it.matches(mode) }
            .map { group ->
                FilterFacet(
                    id = group.id.uuidString(),
                    name = group.name,
                    symbolName = group.symbolName,
                    count = people.count { person -> person.groupList.any { it.id == group.id } },
                )
            }
            .filter { it.count > 0 || it.id == keeping }
            .toMutableList()

        val unplaced = people.count { it.groupList.isEmpty() }
        if ((unplaced > 0 || keeping == FilterFacet.unplacedID) && facets.isNotEmpty()) {
            facets.add(
                FilterFacet(
                    id = FilterFacet.unplacedID,
                    name = "No Place",
                    symbolName = "questionmark.circle",
                    count = unplaced,
                ),
            )
        }
        return facets
    }

    /**
     * Splits people by relationship type, for looking at one place at a time.
     *
     * Someone with two types appears under both — picking one would be
     * inventing an answer. Anyone with no type lands in a final section rather
     * than vanishing.
     */
    fun byType(people: List<Person>): List<PlaceSection> {
        val order = mutableListOf<String>()
        val buckets = mutableMapOf<String, MutableList<Person>>()

        for (person in people) {
            val names = person.tagList
                .sortedBy { it.sortOrder }
                .map { it.name }

            for (name in names.ifEmpty { listOf(NO_TYPE_YET) }) {
                if (buckets[name] == null) {
                    buckets[name] = mutableListOf()
                    order.add(name)
                }
                buckets[name]?.add(person)
            }
        }

        return order
            .sortedByLess { lhs, rhs ->
                // The unclassified pile sorts last wherever it appears.
                when {
                    lhs == NO_TYPE_YET -> false
                    rhs == NO_TYPE_YET -> true
                    else -> {
                        val left = buckets[lhs]?.size ?: 0
                        val right = buckets[rhs]?.size ?: 0
                        if (left == right) swiftCompare(lhs, rhs) < 0 else left > right
                    }
                }
            }
            .map { title -> PlaceSection(title, sort(buckets[title] ?: emptyList(), PeopleSort.NAME)) }
    }

    /** Groups people into A–Z sections. Anything not starting with a letter lands in "#", last. */
    fun alphabeticalSections(people: List<Person>): List<PersonSection> {
        val sorted = sort(people, PeopleSort.NAME)
        val order = mutableListOf<String>()
        val buckets = mutableMapOf<String, MutableList<Person>>()

        for (person in sorted) {
            val first = firstGrapheme(person.sortKey)?.uppercase() ?: "#"
            val key = if (containsLetter(first)) first else "#"
            if (buckets[key] == null) {
                buckets[key] = mutableListOf()
                order.add(key)
            }
            buckets[key]?.add(person)
        }

        return order
            .sortedByLess { lhs, rhs ->
                when {
                    lhs == "#" -> false
                    rhs == "#" -> true
                    else -> swiftCompare(lhs, rhs) < 0
                }
            }
            .map { PersonSection(it, buckets[it] ?: emptyList()) }
    }

    private const val NO_TYPE_YET = "No Type Yet"
}

// MARK: - Swift semantics helpers (private to this file)

/** `sorted(by:)` from a strict "less than" predicate, stably. */
private fun <T> List<T>.sortedByLess(less: (T, T) -> Boolean): List<T> =
    sortedWith { a, b -> if (less(a, b)) -1 else if (less(b, a)) 1 else 0 }

/** Swift's `String <`: Unicode scalar order of the NFC forms. */
private fun swiftCompare(a: String, b: String): Int {
    val x = Normalizer.normalize(a, Normalizer.Form.NFC).codePoints().toArray()
    val y = Normalizer.normalize(b, Normalizer.Form.NFC).codePoints().toArray()
    for (i in 0 until minOf(x.size, y.size)) {
        if (x[i] != y[i]) return x[i].compareTo(y[i])
    }
    return x.size.compareTo(y.size)
}

/** `String.first` — the first extended grapheme cluster, or null when empty. */
private fun firstGrapheme(text: String): String? {
    if (text.isEmpty()) return null
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    return text.substring(0, iterator.next())
}

/** `rangeOfCharacter(from: .letters) != nil` — `.letters` is Unicode L* and M*. */
private fun containsLetter(text: String): Boolean = text.codePoints().anyMatch { cp ->
    Character.isLetter(cp) || when (Character.getType(cp).toByte()) {
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true
        else -> false
    }
}

/** Foundation's `uuidString`: upper-case, as iOS stores and compares it. */
private fun UUID.uuidString(): String = toString().uppercase()

private val uuidPattern = Regex("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")

/** `UUID(uuidString:)` — only the canonical 8-4-4-4-12 form, unlike lenient `UUID.fromString`. */
private fun parseUUID(raw: String): UUID? =
    if (uuidPattern.matches(raw)) UUID.fromString(raw) else null
