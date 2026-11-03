package com.chrisallenclark.keepr.core.domain

import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.KeeprCalendar
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import java.time.Instant

// Translated from Keepr/Domain/FollowUpEngine.swift.

/** Sections of the Follow Up tab, in the order they're shown. */
enum class FollowUpBucket(val rawValue: String, val title: String) {
    OVERDUE("overdue", "Overdue"),
    TODAY("today", "Today"),
    THIS_WEEK("thisWeek", "This Week"),
    LATER("later", "Later"),
    COMPLETED("completed", "Completed");

    val id: String get() = rawValue

    companion object {
        fun from(raw: String?): FollowUpBucket? = entries.firstOrNull { it.rawValue == raw }
    }
}

/** One section of the Follow Up tab. */
data class FollowUpSection(val bucket: FollowUpBucket, val items: List<FollowUp>) {
    val id: String get() = bucket.rawValue
    val title: String get() = bucket.title
}

/** Pure due-date logic. Every rule the UI relies on lives here. */
object FollowUpEngine {

    /** Only follow-ups attached to people visible in the current mode. */
    fun visible(followUps: List<FollowUp>, mode: ContextMode): List<FollowUp> =
        followUps.filter { followUp ->
            val person = followUp.person ?: return@filter false
            person.status == RelationshipStatus.ACTIVE && person.context.matches(mode)
        }

    /**
     * Overdue first, then due today. Within each, high priority and then
     * earliest due date win.
     */
    fun needingAttention(
        followUps: List<FollowUp>,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): List<FollowUp> =
        followUps
            .filter { it.needsAttention(now, calendar) }
            .sortedWith { lhs, rhs ->
                val lOverdue = lhs.isOverdue(now, calendar)
                val rOverdue = rhs.isOverdue(now, calendar)
                when {
                    lOverdue != rOverdue -> if (lOverdue) -1 else 1
                    lhs.priority.weight != rhs.priority.weight -> rhs.priority.weight.compareTo(lhs.priority.weight)
                    else -> lhs.dueDate.compareTo(rhs.dueDate)
                }
            }

    /** Open follow-ups due after today, within the given window. */
    fun upcoming(
        followUps: List<FollowUp>,
        withinDays: Int,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): List<FollowUp> {
        val startOfToday = calendar.startOfDay(now)
        val startOfTomorrow = calendar.addingDays(1, startOfToday)
        val end = calendar.addingDays(withinDays, startOfToday)

        return followUps
            .filter { !it.isCompleted && it.dueDate >= startOfTomorrow && it.dueDate <= end }
            .sortedBy { it.dueDate }
    }

    fun bucket(
        followUp: FollowUp,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): FollowUpBucket {
        if (followUp.isCompleted) return FollowUpBucket.COMPLETED
        if (followUp.isOverdue(now, calendar)) return FollowUpBucket.OVERDUE
        if (followUp.isDueToday(now, calendar)) return FollowUpBucket.TODAY

        val endOfWeek = calendar.addingDays(7, calendar.startOfDay(now))
        return if (followUp.dueDate <= endOfWeek) FollowUpBucket.THIS_WEEK else FollowUpBucket.LATER
    }

    /**
     * Grouped for the Follow Up tab. Empty buckets are dropped; completed
     * items are limited so the list doesn't become an archive.
     */
    fun grouped(
        followUps: List<FollowUp>,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
        completedLimit: Int = 20,
    ): List<FollowUpSection> {
        val buckets = mutableMapOf<FollowUpBucket, MutableList<FollowUp>>()
        for (followUp in followUps) {
            buckets.getOrPut(bucket(followUp, now, calendar)) { mutableListOf() }.add(followUp)
        }

        return FollowUpBucket.entries.mapNotNull { bucket ->
            var items: List<FollowUp> = buckets[bucket]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (bucket == FollowUpBucket.COMPLETED) {
                items = items
                    .sortedWith { a, b -> (b.completedAt ?: b.dueDate).compareTo(a.completedAt ?: a.dueDate) }
                    .take(completedLimit)
            } else {
                items = items.sortedWith { lhs, rhs ->
                    if (lhs.dueDate != rhs.dueDate) lhs.dueDate.compareTo(rhs.dueDate)
                    else rhs.priority.weight.compareTo(lhs.priority.weight)
                }
            }
            FollowUpSection(bucket, items)
        }
    }

    // Suggested due dates

    /** The presets offered when creating a follow-up. */
    enum class DuePreset(val rawValue: String, val title: String, val days: Int) {
        TOMORROW("tomorrow", "Tomorrow", 1),
        IN_THREE_DAYS("inThreeDays", "In 3 Days", 3),
        NEXT_WEEK("nextWeek", "Next Week", 7),
        IN_TWO_WEEKS("inTwoWeeks", "In 2 Weeks", 14),
        NEXT_MONTH("nextMonth", "Next Month", 30);

        val id: String get() = rawValue

        fun date(from: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): Instant =
            calendar.addingDays(days, calendar.startOfDay(from))

        companion object {
            fun from(raw: String?): DuePreset? = entries.firstOrNull { it.rawValue == raw }
        }
    }
}
