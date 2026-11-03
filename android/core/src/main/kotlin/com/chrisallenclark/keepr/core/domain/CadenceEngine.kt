package com.chrisallenclark.keepr.core.domain

import com.chrisallenclark.keepr.core.model.KeeprCalendar
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import java.text.Normalizer
import java.time.Instant
import java.util.UUID

// Translated from Keepr/Domain/CadenceEngine.swift.

/** Where a person stands against the rhythm you set for them. */
data class CadenceStatus(
    /** The interval being applied, in days. */
    val days: Int,
    /** Where it came from, for showing "every 30 days · Current Client". */
    val source: String?,
    /** When the next contact is due. */
    val dueDate: Instant,
    /** Positive when overdue, negative when there's still time. */
    val daysOverdue: Int,
) {
    val isOverdue: Boolean get() = daysOverdue >= 0

    /** "Due today", "3 days over", "in 5 days" — the phrase a person reads. */
    val summary: String
        get() = when {
            daysOverdue == 0 -> "Due today"
            daysOverdue == 1 -> "1 day over"
            daysOverdue > 1 -> "$daysOverdue days over"
            daysOverdue == -1 -> "Due tomorrow"
            else -> "In ${-daysOverdue} days"
        }
}

/** One person and where they stand, computed once. */
data class CadenceItem(val person: Person, val status: CadenceStatus) {
    val id: UUID get() = person.id
}

/** The interval applied to a person and where it came from (nil source = their own). */
data class Cadence(val days: Int, val source: String?)

/**
 * Turns "clients every 30 days" into "these four are overdue".
 *
 * Cadence is a property of a *relationship type*, not of a person: deciding
 * once that clients are worth a month is a minute's work, deciding it per
 * client is a chore nobody finishes. A person-level override exists for the
 * few who are genuinely different, and zero means "never chase me about this
 * one". Nothing here invents urgency: no cadence anywhere, nothing said.
 */
object CadenceEngine {

    /** Intervals offered in the pickers. Real rhythms people keep, not a slider. */
    val presets: List<Int> = listOf(7, 14, 30, 60, 90, 180, 365)

    /**
     * The interval to apply to one person: their own if they have one, else the
     * most demanding of their types — someone who is both a Current Client (30)
     * and a Friend (90) needs contact every 30 days.
     */
    fun cadence(person: Person): Cadence? {
        person.cadenceDays?.let { own -> return if (own <= 0) null else Cadence(own, null) }

        val candidates = person.tagList.mapNotNull { tag ->
            val days = tag.cadenceDays ?: return@mapNotNull null
            if (days > 0) Cadence(days, tag.name) else null
        }
        // `min(by:)` keeps the first of equal minimums, as minByOrNull does.
        return candidates.minByOrNull { it.days }
    }

    /**
     * Where a person stands right now, or nil when no rhythm applies.
     *
     * The clock runs from the last logged interaction, or from when they were
     * added — so someone imported today isn't instantly overdue, and logging
     * anything resets it with no extra step.
     */
    fun status(
        person: Person,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): CadenceStatus? {
        if (person.status != RelationshipStatus.ACTIVE) return null
        val cadence = cadence(person) ?: return null

        val anchor = person.lastInteractionAt ?: person.createdAt
        val due = calendar.addingDays(cadence.days, anchor)
        val days = calendar.dayStartsBetween(due, now)

        return CadenceStatus(days = cadence.days, source = cadence.source, dueDate = due, daysOverdue = days)
    }

    /**
     * Everyone who is due or overdue, most overdue first. People with an open
     * follow-up are left out: a plan already exists, and listing them twice
     * would feel like nagging.
     */
    fun due(
        people: List<Person>,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): List<CadenceItem> =
        people
            .mapNotNull { person ->
                if (person.openFollowUps.isNotEmpty()) return@mapNotNull null
                val status = status(person, now, calendar) ?: return@mapNotNull null
                if (!status.isOverdue) return@mapNotNull null
                CadenceItem(person, status)
            }
            .sortedWith { lhs, rhs ->
                if (lhs.status.daysOverdue == rhs.status.daysOverdue) {
                    swiftOrder.compare(lhs.person.sortKey, rhs.person.sortKey)
                } else {
                    rhs.status.daysOverdue.compareTo(lhs.status.daysOverdue)
                }
            }

    /** True when a rhythm governs this person, so "going quiet" should keep quiet. */
    fun hasCadence(person: Person): Boolean = cadence(person) != null

    /** "Every 30 days" / "Every 3 months" — how an interval is written. */
    fun label(forDays: Int): String = when (forDays) {
        1 -> "Every day"
        7 -> "Every week"
        14 -> "Every 2 weeks"
        30 -> "Every month"
        60 -> "Every 2 months"
        90 -> "Every 3 months"
        180 -> "Every 6 months"
        365 -> "Every year"
        else -> "Every $forDays days"
    }

    /**
     * Swift's `String <`: canonical-equivalence aware, ordered by Unicode
     * scalar value (not UTF-16 unit, which differs for astral characters).
     */
    internal val swiftOrder: Comparator<String> = Comparator { a, b ->
        val x = Normalizer.normalize(a, Normalizer.Form.NFC).codePoints().toArray()
        val y = Normalizer.normalize(b, Normalizer.Form.NFC).codePoints().toArray()
        for (i in 0 until minOf(x.size, y.size)) {
            if (x[i] != y[i]) return@Comparator x[i].compareTo(y[i])
        }
        x.size.compareTo(y.size)
    }
}
