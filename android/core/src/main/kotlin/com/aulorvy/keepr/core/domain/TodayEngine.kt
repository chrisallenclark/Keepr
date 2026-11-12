package com.aulorvy.keepr.core.domain

import com.aulorvy.keepr.core.model.ContextMode
import com.aulorvy.keepr.core.model.FollowUp
import com.aulorvy.keepr.core.model.KeeprCalendar
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.Priority
import java.time.Instant

// Translated from Keepr/Domain/TodayEngine.swift.

/** Everything the Today screen shows, computed in one pass. */
data class TodayDigest(
    val needsAttention: List<FollowUp> = emptyList(),
    /** People whose own rhythm says it's time, most overdue first. */
    val dueForContact: List<CadenceItem> = emptyList(),
    val upcoming: List<FollowUp> = emptyList(),
    val goingQuiet: List<Person> = emptyList(),
    val recent: List<Person> = emptyList(),
) {
    val isEmpty: Boolean
        get() = needsAttention.isEmpty() && dueForContact.isEmpty() && upcoming.isEmpty() &&
            goingQuiet.isEmpty() && recent.isEmpty()

    /** True when there's genuinely nothing to act on — the calm state. */
    val isCaughtUp: Boolean get() = needsAttention.isEmpty() && dueForContact.isEmpty()
}

/**
 * Pure logic behind Today. No engagement mechanics: nothing here invents
 * urgency, it only reflects what the user already told the app.
 */
object TodayEngine {

    /**
     * How long without a logged interaction before a relationship reads as
     * going quiet. Business relationships drift faster than personal ones.
     */
    fun quietThreshold(mode: ContextMode): Int = when (mode) {
        ContextMode.BUSINESS -> 21
        ContextMode.PERSONAL -> 60
    }

    /** How far ahead "Upcoming" looks. */
    const val upcomingWindowDays = 7

    const val goingQuietLimit = 5
    const val recentLimit = 6
    const val upcomingLimit = 5
    const val dueForContactLimit = 8

    fun digest(
        people: List<Person>,
        mode: ContextMode,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): TodayDigest {
        val visible = PeopleEngine.visible(people, mode)
        val followUps = visible.flatMap { it.followUpList }

        return TodayDigest(
            needsAttention = FollowUpEngine.needingAttention(followUps, now, calendar),
            upcoming = FollowUpEngine.upcoming(followUps, upcomingWindowDays, now, calendar).take(upcomingLimit),
            dueForContact = CadenceEngine.due(visible, now, calendar).take(dueForContactLimit),
            goingQuiet = goingQuiet(visible, mode, now, calendar).take(goingQuietLimit),
            recent = recentlyActive(visible, now = now, calendar = calendar).take(recentLimit),
        )
    }

    /**
     * Relationships that have drifted: nothing logged for a while and no plan
     * in place. People with an open follow-up are excluded — they're handled.
     */
    fun goingQuiet(
        people: List<Person>,
        mode: ContextMode,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): List<Person> {
        val threshold = quietThreshold(mode)
        val cutoff = calendar.addingDays(-threshold, now)

        return people
            .filter { person ->
                if (person.openFollowUps.isNotEmpty()) return@filter false
                // A rhythm is a better answer than a blanket threshold, and
                // being told twice about the same person reads as nagging.
                if (CadenceEngine.hasCadence(person)) return@filter false

                val last = person.lastInteractionAt
                if (last != null) return@filter last < cutoff
                // Never contacted: only surface people the user flagged as mattering,
                // so a fresh contact import doesn't fill the screen.
                if (!(person.isFavorite || person.priority == Priority.HIGH)) return@filter false
                person.createdAt < cutoff
            }
            .sortedWith { lhs, rhs ->
                val l = lhs.lastInteractionAt ?: lhs.createdAt
                val r = rhs.lastInteractionAt ?: rhs.createdAt
                l.compareTo(r)
            }
    }

    /**
     * People something happened with lately — an interaction, or simply being
     * added. Adding someone counts: categorizing three new contacts and seeing
     * nothing appear reads as the app losing them, and "I just added these" is
     * exactly the list you want while the memory is fresh.
     */
    fun recentlyActive(
        people: List<Person>,
        withinDays: Int = 14,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): List<Person> {
        val cutoff = calendar.addingDays(-withinDays, now)
        return people
            .mapNotNull { person ->
                // Swift: max(lastInteractionAt ?? .distantPast, createdAt).
                val last = person.lastInteractionAt
                val touched = if (last != null && last > person.createdAt) last else person.createdAt
                if (touched >= cutoff) person to touched else null
            }
            .sortedWith { a, b -> b.second.compareTo(a.second) }
            .map { it.first }
    }
}
