package com.chrisallenclark.keepr.core.domain

import com.chrisallenclark.keepr.core.model.Interaction
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.KeeprCalendar
import com.chrisallenclark.keepr.core.model.ModelContext
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import java.time.Instant
import java.util.UUID

// Translated from Keepr/Domain/Outreach.swift.

/** One person you've reached out to and not heard back from. */
data class WaitingItem(val person: Person, val since: Instant, val days: Int) {
    val id: UUID get() = person.id

    /** "Sent today", "3 days ago" — how long the ball has been in their court. */
    val summary: String
        get() = when (days) {
            0 -> "Sent today"
            1 -> "Sent yesterday"
            else -> "Sent $days days ago"
        }
}

/**
 * Reaching out is not the same as talking.
 *
 * An unanswered text still means you did your part, so the rhythm resets —
 * but it isn't a conversation, so it's recorded as an attempt and the person
 * waits on a reply until something comes back. Collapsing the two would either
 * nag you for doing the right thing or call a one-sided relationship healthy.
 */
object Outreach {

    /** Records an attempt: logged, clock reset, ball in their court. */
    fun markReachedOut(
        person: Person,
        kind: InteractionKind = InteractionKind.TEXT,
        note: String? = null,
        at: Instant = Instant.now(),
        context: ModelContext,
    ): Interaction {
        val interaction = Interaction(
            kind = kind,
            occurredAt = at,
            summary = note ?: "Reached out — no reply yet",
            awaitingReply = true,
            person = person,
        )
        context.insert(interaction)

        person.lastInteractionAt = at
        person.awaitingReplySince = at
        person.touch()
        return interaction
    }

    /**
     * They came back. Clears the waiting flag without inventing an
     * interaction — the user logs what was actually said, if anything was.
     * ([at] is accepted for parity with iOS, which also ignores it.)
     */
    @Suppress("UNUSED_PARAMETER")
    fun markReplied(person: Person, at: Instant = Instant.now()) {
        person.awaitingReplySince = null
        person.touch()
    }

    /** Everyone still waiting, longest first. */
    fun waiting(
        people: List<Person>,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): List<WaitingItem> =
        people
            .mapNotNull { person ->
                if (person.status != RelationshipStatus.ACTIVE) return@mapNotNull null
                val since = person.awaitingReplySince ?: return@mapNotNull null
                val days = calendar.dayStartsBetween(since, now)
                WaitingItem(person, since, maxOf(0, days))
            }
            .sortedBy { it.since }
}
