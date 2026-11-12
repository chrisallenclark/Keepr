package com.aulorvy.keepr.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The parts of Foundation's `Calendar` the iOS logic uses, over `java.time`.
 *
 * Swift's `Date` is an absolute instant; here that's [Instant]. Calendar math
 * (start of day, adding days, "same day") happens in a time zone, exactly as
 * `Calendar.current` does on iOS, so behavior matches across DST changes.
 * Engines take a calendar parameter, as the Swift ones do, which is what lets
 * tests pin UTC and a fixed "now".
 */
class KeeprCalendar(
    val zone: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.getDefault(),
) {
    fun zoned(instant: Instant): ZonedDateTime = instant.atZone(zone)

    fun localDate(instant: Instant): LocalDate = zoned(instant).toLocalDate()

    /** `calendar.startOfDay(for:)` */
    fun startOfDay(instant: Instant): Instant = localDate(instant).atStartOfDay(zone).toInstant()

    /** `calendar.isDate(_:inSameDayAs:)` */
    fun isSameDay(a: Instant, b: Instant): Boolean = localDate(a) == localDate(b)

    /** `calendar.date(byAdding: .day, value:, to:)` — wall-clock days, DST-aware. */
    fun addingDays(days: Int, instant: Instant): Instant = zoned(instant).plusDays(days.toLong()).toInstant()

    fun addingMonths(months: Int, instant: Instant): Instant = zoned(instant).plusMonths(months.toLong()).toInstant()

    fun addingYears(years: Int, instant: Instant): Instant = zoned(instant).plusYears(years.toLong()).toInstant()

    fun addingHours(hours: Int, instant: Instant): Instant = instant.plus(hours.toLong(), ChronoUnit.HOURS)

    fun addingMinutes(minutes: Int, instant: Instant): Instant = instant.plus(minutes.toLong(), ChronoUnit.MINUTES)

    /** `calendar.date(bySettingHour:minute:second:of:)` */
    fun settingTime(hour: Int, minute: Int = 0, second: Int = 0, instant: Instant): Instant =
        localDate(instant).atTime(LocalTime.of(hour, minute, second)).atZone(zone).toInstant()

    /** `calendar.date(from: DateComponents(year:month:day:hour:minute:))` */
    fun date(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute, second).atZone(zone).toInstant()

    /** Whole calendar days from [from] to [to]: `dateComponents([.day], from:to:).day`. */
    fun daysBetween(from: Instant, to: Instant): Int =
        ChronoUnit.DAYS.between(zoned(from), zoned(to)).toInt()

    /** Whole calendar days between the starts of the two days. */
    fun dayStartsBetween(from: Instant, to: Instant): Int =
        ChronoUnit.DAYS.between(localDate(from), localDate(to)).toInt()

    fun year(instant: Instant): Int = zoned(instant).year
    fun month(instant: Instant): Int = zoned(instant).monthValue
    fun day(instant: Instant): Int = zoned(instant).dayOfMonth
    fun hour(instant: Instant): Int = zoned(instant).hour
    fun minute(instant: Instant): Int = zoned(instant).minute

    /** Foundation's weekday numbering: 1 = Sunday … 7 = Saturday. */
    fun weekday(instant: Instant): Int = zoned(instant).dayOfWeek.let { if (it == DayOfWeek.SUNDAY) 1 else it.value + 1 }

    // Formatting, matching the iOS `Date.FormatStyle`s the app uses.

    /** `.dateTime.weekday(.wide)` — "Saturday". */
    fun weekdayName(instant: Instant): String =
        DateTimeFormatter.ofPattern("EEEE", locale).format(zoned(instant))

    /** `.dateTime.month(.abbreviated).day()` — "Oct 8". */
    fun monthDay(instant: Instant): String =
        DateTimeFormatter.ofPattern("MMM d", locale).format(zoned(instant))

    /** `.dateTime.month(.abbreviated).day().year()` — "Oct 8, 2024". */
    fun monthDayYear(instant: Instant): String =
        DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(zoned(instant))

    /** `.dateTime.month(.wide).day()` — "October 8". */
    fun fullMonthDay(instant: Instant): String =
        DateTimeFormatter.ofPattern("MMMM d", locale).format(zoned(instant))

    /** `formatted(date: .omitted, time: .shortened)` — "3:30 PM". */
    fun shortTime(instant: Instant): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(zoned(instant))
            .replace(' ', ' ')

    /** `formatted(date: .abbreviated, time: .omitted)` — "Oct 8, 2024". */
    fun abbreviatedDate(instant: Instant): String = monthDayYear(instant)

    companion object {
        /** `Calendar.current`. */
        fun current(): KeeprCalendar = KeeprCalendar()

        /** Gregorian, UTC, POSIX-like English — what the iOS test suite pins. */
        val utc: KeeprCalendar = KeeprCalendar(ZoneOffset.UTC, Locale.US)
    }
}
