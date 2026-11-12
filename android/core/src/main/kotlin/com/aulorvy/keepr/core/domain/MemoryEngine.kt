package com.aulorvy.keepr.core.domain

import com.aulorvy.keepr.core.model.Memory
import com.aulorvy.keepr.core.model.MemoryCategory
import com.aulorvy.keepr.core.model.Priority

// Translated from Keepr/Domain/MemoryEngine.swift.

/** One category's worth of what you know about someone. */
data class MemoryGroup(
    val category: MemoryCategory,
    val memories: List<Memory>,
) {
    val id: String get() = category.rawValue
    val count: Int get() = memories.size
}

/** A fact sitting in Other that a fresh pass would now file under [category]. */
data class RefileProposal(val memory: Memory, val category: MemoryCategory)

/**
 * Deciding what a profile shows, and what it holds back.
 *
 * A brain dump can produce fifteen facts in one go, and fifteen facts printed
 * down a profile is a wall nobody reads. So the profile shows a few, says how
 * much else exists, and puts the rest one tap away. Nothing is hidden
 * permanently; it's filed.
 */
object MemoryEngine {

    /** How many facts a profile shows before deferring to the full list. */
    const val headlineLimit: Int = 3

    /**
     * The order categories are shown in: what someone is, then what they want,
     * then the softer detail — roughly the order you'd want them in your head
     * walking into a room.
     */
    val categoryOrder: List<MemoryCategory> = listOf(
        MemoryCategory.WORK, MemoryCategory.BUSINESS, MemoryCategory.OPPORTUNITY, MemoryCategory.GOALS,
        MemoryCategory.PROGRESS, MemoryCategory.HEALTH, MemoryCategory.SCHEDULE, MemoryCategory.FAMILY,
        MemoryCategory.IMPORTANT_DATE, MemoryCategory.STYLE, MemoryCategory.PREFERENCES,
        MemoryCategory.INTERESTS, MemoryCategory.PERSONAL, MemoryCategory.OTHER,
    )

    /**
     * The few facts worth printing on the profile itself.
     *
     * Spread across categories before depth: three facts about someone's kids
     * reads like a filing error, while "runs a roofing crew / wants to lose 20
     * lb / daughter's wedding in spring" reads like you know them. Starred facts
     * still win — those are the ones the user said matter.
     */
    fun headline(memories: List<Memory>, limit: Int = headlineLimit): List<Memory> {
        val candidates = memories.filter { !it.isArchived }
        if (candidates.isEmpty()) return emptyList()

        val starred = candidates
            .filter { it.importance == Priority.HIGH }
            .sortedByDescending { it.createdAt }

        val chosen = mutableListOf<Memory>()
        val usedCategories = mutableSetOf<String>()

        fun take(pool: List<Memory>, allowRepeats: Boolean) {
            for (memory in pool) {
                if (chosen.size >= limit) continue
                val key = memory.categoryRaw
                if (!allowRepeats && key in usedCategories) continue
                if (chosen.any { it.id == memory.id }) continue
                chosen += memory
                usedCategories += key
            }
        }

        val rest = candidates
            .filter { it.importance != Priority.HIGH }
            .sortedByDescending { it.createdAt }

        take(starred, allowRepeats = false)
        take(rest, allowRepeats = false)
        // Only once every category has had a turn does depth get a look in.
        take(starred + rest, allowRepeats = true)

        return chosen
    }

    /**
     * Everything, filed by category, in reading order. Empty categories are left
     * out rather than shown as zeroes.
     */
    fun grouped(memories: List<Memory>): List<MemoryGroup> {
        val visible = memories.filter { !it.isArchived }

        return categoryOrder.mapNotNull { category ->
            val matching = visible
                .filter { it.category == category }
                .sortedWith(
                    compareByDescending<Memory> { it.importance.weight }.thenByDescending { it.createdAt },
                )
            if (matching.isEmpty()) null else MemoryGroup(category, matching)
        }
    }

    /** The category pills under the headline facts: what else is in there, and how much. */
    fun summaryCounts(memories: List<Memory>): List<MemoryGroup> = grouped(memories)

    /**
     * Facts sitting in Other that a fresh pass would now file somewhere.
     *
     * Categorization improves between releases and facts already on record
     * don't benefit retroactively. This offers the re-file rather than
     * performing it: silently rewriting the user's own filing decisions would be
     * worse than leaving them wrong. [classify] is injected so this stays
     * testable without the extractor.
     */
    fun refilable(memories: List<Memory>, classify: (String) -> MemoryCategory): List<RefileProposal> =
        memories
            .filter { it.category == MemoryCategory.OTHER && !it.isArchived }
            .mapNotNull { memory ->
                val proposed = classify(memory.content)
                if (proposed == MemoryCategory.OTHER) null else RefileProposal(memory, proposed)
            }

    /** "14 things" / "1 thing" — used on the button that opens the full list. */
    fun totalLabel(count: Int): String = if (count == 1) "1 thing" else "$count things"
}
