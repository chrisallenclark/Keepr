package com.aulorvy.keepr.core.domain

import com.aulorvy.keepr.core.model.FollowUp
import com.aulorvy.keepr.core.model.KeeprCalendar
import java.time.Instant

/**
 * Short, human date strings used across lists, so "3 days ago" reads the same
 * on Today, a profile timeline and a follow-up row.
 * Translated from Keepr/Domain/RelativeDate.swift.
 */
object RelativeDate {

    /** Past-leaning: "Today", "Yesterday", "3 days ago", "Aug 13", "Aug 13, 2024". */
    fun past(date: Instant, now: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): String {
        // Measured against `now`, never the system clock.
        val days = dayOffset(date, now, calendar)
        if (days == 0) return "Today"
        if (days == 1) return "Yesterday"
        if (days in 2..6) return "$days days ago"
        return absolute(date, now, calendar)
    }

    /** Future-leaning: "Today", "Tomorrow", "Yesterday", "Friday", "Sep 3". */
    fun due(date: Instant, now: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): String {
        val days = dayOffset(now, date, calendar)
        if (days == 0) return "Today"
        if (days == 1) return "Tomorrow"
        if (days == -1) return "Yesterday"
        if (days < 0) {
            val overdue = -days
            return if (overdue < 7) "$overdue days ago" else absolute(date, now, calendar)
        }
        if (days < 7) return calendar.weekdayName(date)
        return absolute(date, now, calendar)
    }

    /** Whole days between two instants, ignoring time of day. */
    fun dayOffset(start: Instant, end: Instant, calendar: KeeprCalendar = KeeprCalendar.current()): Int =
        calendar.dayStartsBetween(start, end)

    /** "Aug 13" this year, "Aug 13, 2024" otherwise. */
    fun absolute(date: Instant, now: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): String =
        if (calendar.year(date) == calendar.year(now)) calendar.monthDay(date) else calendar.monthDayYear(date)

    /** The "last contact" line: a relative date, or a plain statement when there's none. */
    fun lastContact(date: Instant?, now: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): String {
        if (date == null) return "No interactions logged"
        return past(date, now, calendar)
    }

    /** "Friday at 9:00 AM" for a follow-up with a set time; just the day otherwise. */
    fun dueWithTime(followUp: FollowUp, now: Instant = Instant.now(), calendar: KeeprCalendar = KeeprCalendar.current()): String {
        val day = due(followUp.dueDate, now, calendar)
        if (!followUp.hasTime) return day
        return "$day at ${calendar.shortTime(followUp.dueDate)}"
    }
}
