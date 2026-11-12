package com.aulorvy.keepr.core.services

import com.aulorvy.keepr.core.model.KeeprCalendar
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** A date found in free text: where it sits, what it resolves to, and whether a time was given. */
data class DetectedDate(
    /** Character (UTF-16) offsets of the whole phrase in the searched text. */
    val range: IntRange,
    val date: Instant,
    val hasTime: Boolean,
)

/**
 * The stand-in for `NSDataDetector(types: .date)`, which has no JVM or Android
 * equivalent. It finds the English date and time phrases people type into a
 * note and resolves them the way Apple's detector does, as closely as its
 * observable behavior allows.
 *
 * What it recognizes (case-insensitive, on word boundaries):
 * - **Relative days**: "today", "tomorrow" (also "tmrw"), "yesterday",
 *   "the day after tomorrow", "the day before yesterday", "tonight",
 *   "this morning", "this afternoon", "this evening".
 * - **Weekdays**: full names ("Tuesday"), optionally after "on", "this",
 *   "next", "coming", "this coming" or "last". Abbreviations ("Tue", "Thurs")
 *   only after one of those words, so "sat down with" and "sun" in prose are
 *   left alone.
 * - **Month-name dates**: "June 20", "Jun. 20th", "June 20, 2026", "20 June",
 *   "the 20th of June 2026". Lowercase "may" is never a month — it's a verb far
 *   more often than not; "May 5" is.
 * - **Numeric dates**: US order "6/20", "6/20/26", "6/20/2026", and ISO
 *   "2026-06-20". Two-digit years are 20xx.
 * - **Ordinal days**: "the 14th", "on the 3rd", "on 21st".
 * - **Offsets**: "in 3 days", "in two weeks", "in a month", "in 2 hours",
 *   "in 30 minutes" (counts as digits or "a"/"an"/one…twelve).
 * - **Times**: "3pm", "3 p.m.", "3:30", "3:30pm", "15:30", "at noon", and a bare
 *   hour after "at"/"around" ("at 3") when the phrase ends there or a date
 *   follows. A time joins a neighboring date — "tomorrow at 3pm", "3pm on
 *   Friday", "June 20, 5:30" — and "morning"/"afternoon"/"evening"/"night" after
 *   a day or an hour set the part of day ("Friday evening", "8 in the morning").
 *
 * How phrases resolve, against `now` in the calendar's time zone:
 * - A date without a time is that day at 12:00, as `NSDataDetector` reports
 *   it; "tonight"/"this evening" are 20:00/19:00, "morning" 09:00, "afternoon"
 *   15:00, "night" 21:00.
 * - A bare or "next"/"coming"/"on" weekday is the first such day after today —
 *   never today itself. "this <weekday>" may be today; "last" is the previous one.
 * - A month and day without a year is the next occurrence: this year if that
 *   day hasn't passed (today counts), otherwise next year. Feb 29 waits for a
 *   leap year. An ordinal day alone is this month if not yet past, else the next
 *   month that has that day.
 * - A time on its own is today at that time, even if it has passed — the
 *   detector reports the date; callers decide whether past dates count.
 * - An hour with no am/pm: 1–6 is afternoon, 7–11 morning, 12 noon, 0 and
 *   13–23 are 24-hour; a part-of-day word overrides the guess.
 * - Impossible dates ("2/30", "13/1") are not matches.
 *
 * Known differences from `NSDataDetector`: Apple's grammar is undocumented and
 * larger. Not recognized here: "next week"/"next month"/"next year" as dates
 * (the extractor's own table handles the first two before detection), "Tuesday
 * next week", "a week from Friday", "June" or "June 2026" alone, "midnight",
 * written-out days ("June twentieth"), day-first numeric dates, and time zones in
 * the text. Apple's exact choices for "next <weekday>" (some readings mean the
 * week after) and for am/pm-less hours are not published; the rules above are the
 * common readings. Apple resolves against the system clock and zone; this
 * takes `now` and a [KeeprCalendar] so results are deterministic.
 */
object DateDetector {

    fun matches(
        text: String,
        now: Instant = Instant.now(),
        calendar: KeeprCalendar = KeeprCalendar.current(),
    ): List<DetectedDate> {
        val today = calendar.localDate(now)
        val candidates = mutableListOf<Piece>()
        collectDays(text, today, candidates)
        collectTimes(text, candidates)
        collectOffsets(text, now, today, calendar, candidates)

        // Longest phrase wins where candidates overlap ("the 20th of June" over "the 20th").
        val chosen = mutableListOf<Piece>()
        for (piece in candidates.sortedWith(compareByDescending<Piece> { it.end - it.start }.thenBy { it.start })) {
            if (chosen.none { it.start < piece.end && piece.start < it.end }) chosen += piece
        }
        chosen.sortBy { it.start }

        return combine(text, chosen, today, calendar)
    }

    // MARK: - Pieces

    private enum class PartOfDay(val time: LocalTime, val afternoon: Boolean) {
        MORNING(LocalTime.of(9, 0), false),
        AFTERNOON(LocalTime.of(15, 0), true),
        EVENING(LocalTime.of(19, 0), true),
        NIGHT(LocalTime.of(21, 0), true),
    }

    private sealed class Piece {
        abstract val start: Int
        abstract val end: Int
    }

    private data class DayPiece(
        override val start: Int,
        override val end: Int,
        val day: LocalDate,
        val isWeekday: Boolean = false,
        val defaultTime: LocalTime = NOON,
        val part: PartOfDay? = null,
    ) : Piece()

    /** [hour] is 24-hour unless [ambiguous] (no am/pm, 1–12). */
    private data class TimePiece(
        override val start: Int,
        override val end: Int,
        val hour: Int,
        val minute: Int,
        val second: Int = 0,
        val ambiguous: Boolean = false,
    ) : Piece()

    private data class PartPiece(override val start: Int, override val end: Int, val part: PartOfDay) : Piece()

    private data class MomentPiece(override val start: Int, override val end: Int, val instant: Instant) : Piece()

    private val NOON: LocalTime = LocalTime.of(12, 0)

    // MARK: - Vocabulary

    private const val B = "(?<![\\p{L}\\p{N}])"
    private const val E = "(?![\\p{L}\\p{N}])"

    private val months: Map<String, Int> = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6, "jul" to 7, "aug" to 8,
        "sept" to 9, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )
    private val monthAlternation = months.keys.sortedByDescending { it.length }.joinToString("|")

    private val weekdays: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
    )
    private val weekdayAbbreviations: Map<String, DayOfWeek> = mapOf(
        "mon" to DayOfWeek.MONDAY, "tues" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "thurs" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY,
    )
    private val fullWeekdayAlternation = weekdays.keys.joinToString("|")
    private val abbreviatedWeekdayAlternation = weekdayAbbreviations.keys.sortedByDescending { it.length }.joinToString("|")

    private val numberWords: Map<String, Int> = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )

    private fun regex(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private const val ORDINAL = "(?:st|nd|rd|th)"
    private const val YEAR_TAIL = "(?:,?\\s+(\\d{4})$E)?"
    private const val MODIFIER = "(this\\s+coming|next|this|coming|last|on)"

    private val monthDay = regex("$B($monthAlternation)$E\\.?\\s+(\\d{1,2})$ORDINAL?$E$YEAR_TAIL")
    private val dayMonth = regex("$B(?:the\\s+)?(\\d{1,2})$ORDINAL?\\s+(?:of\\s+)?($monthAlternation)$E\\.?$YEAR_TAIL")
    private val numericDate = regex("(?<![\\p{L}\\p{N}/.\\-])(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}|\\d{2}))?(?![\\p{L}\\p{N}/])")
    private val isoDate = regex("$B(\\d{4})-(\\d{1,2})-(\\d{1,2})$E")
    private val ordinalDay = regex("$B(?:(?:on\\s+)?the|on)\\s+(\\d{1,2})$ORDINAL$E")
    private val fullWeekday = regex("$B(?:$MODIFIER\\s+)?($fullWeekdayAlternation)$E")
    private val abbreviatedWeekday = regex("$B$MODIFIER\\s+($abbreviatedWeekdayAlternation)$E\\.?")
    private val relativeDay = regex(
        "$B(the\\s+day\\s+after\\s+tomorrow|day\\s+after\\s+tomorrow|the\\s+day\\s+before\\s+yesterday|" +
            "day\\s+before\\s+yesterday|today|tomorrow|tmrw|yesterday|tonight|" +
            "this\\s+morning|this\\s+afternoon|this\\s+evening)$E",
    )
    private val partOfDay = regex("$B(?:in\\s+the\\s+)?(morning|afternoon|evening|night)$E")

    private const val AT = "(?:(?:at|@|around)\\s+)?"
    private const val MERIDIEM = "(a\\.m\\.|p\\.m\\.|am|pm)"
    private val clockTime = regex("$B$AT(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:\\s*$MERIDIEM)?(?![\\p{L}\\p{N}:])")
    private val hourMeridiem = regex("$B$AT(\\d{1,2})\\s*$MERIDIEM(?![\\p{L}\\p{N}])")
    private val bareHour = regex(
        "$B(?:at|@|around)\\s+(\\d{1,2})$E(?=\\s*(?:$|[.,;!?)]|(?:today|tonight|tomorrow|on|this|next|in\\s+the|" +
            "$fullWeekdayAlternation)$E))",
    )
    private val noon = regex("$B(?:at\\s+)?(noon|midday)$E")
    private val offset = regex(
        "${B}in\\s+(\\d{1,3}|${numberWords.keys.sortedByDescending { it.length }.joinToString("|")})\\s+" +
            "(minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?)$E",
    )

    // MARK: - Collecting

    private fun collectDays(text: String, today: LocalDate, out: MutableList<Piece>) {
        for (m in monthDay.findAll(text)) {
            val name = m.groupValues[1]
            if (name == "may") continue
            val day = monthDayDate(months.getValue(name.lowercase()), m.groupValues[2].toInt(), m.groupValues[3], today)
            if (day != null) out += DayPiece(m.range.first, m.range.last + 1, day)
        }
        for (m in dayMonth.findAll(text)) {
            val name = m.groupValues[2]
            if (name == "may") continue
            val day = monthDayDate(months.getValue(name.lowercase()), m.groupValues[1].toInt(), m.groupValues[3], today)
            if (day != null) out += DayPiece(m.range.first, m.range.last + 1, day)
        }
        for (m in numericDate.findAll(text)) {
            val day = monthDayDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], today)
            if (day != null) out += DayPiece(m.range.first, m.range.last + 1, day)
        }
        for (m in isoDate.findAll(text)) {
            val day = validDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            if (day != null) out += DayPiece(m.range.first, m.range.last + 1, day)
        }
        for (m in ordinalDay.findAll(text)) {
            val day = nextDayOfMonth(m.groupValues[1].toInt(), today)
            if (day != null) out += DayPiece(m.range.first, m.range.last + 1, day)
        }
        for (m in fullWeekday.findAll(text)) {
            val day = weekdayDate(weekdays.getValue(m.groupValues[2].lowercase()), m.groupValues[1], today)
            out += DayPiece(m.range.first, m.range.last + 1, day, isWeekday = true)
        }
        for (m in abbreviatedWeekday.findAll(text)) {
            val day = weekdayDate(weekdayAbbreviations.getValue(m.groupValues[2].lowercase()), m.groupValues[1], today)
            out += DayPiece(m.range.first, m.range.last + 1, day, isWeekday = true)
        }
        for (m in relativeDay.findAll(text)) {
            val phrase = m.groupValues[1].lowercase().replace(Regex("\\s+"), " ")
            val piece = when (phrase) {
                "today" -> DayPiece(0, 0, today)
                "tomorrow", "tmrw" -> DayPiece(0, 0, today.plusDays(1))
                "yesterday" -> DayPiece(0, 0, today.minusDays(1))
                "the day after tomorrow", "day after tomorrow" -> DayPiece(0, 0, today.plusDays(2))
                "the day before yesterday", "day before yesterday" -> DayPiece(0, 0, today.minusDays(2))
                "tonight" -> DayPiece(0, 0, today, defaultTime = LocalTime.of(20, 0), part = PartOfDay.NIGHT)
                "this morning" -> DayPiece(0, 0, today, defaultTime = PartOfDay.MORNING.time, part = PartOfDay.MORNING)
                "this afternoon" -> DayPiece(0, 0, today, defaultTime = PartOfDay.AFTERNOON.time, part = PartOfDay.AFTERNOON)
                else -> DayPiece(0, 0, today, defaultTime = PartOfDay.EVENING.time, part = PartOfDay.EVENING)
            }
            out += piece.copy(start = m.range.first, end = m.range.last + 1)
        }
        for (m in partOfDay.findAll(text)) {
            out += PartPiece(m.range.first, m.range.last + 1, PartOfDay.valueOf(m.groupValues[1].uppercase()))
        }
    }

    private fun collectTimes(text: String, out: MutableList<Piece>) {
        for (m in clockTime.findAll(text)) {
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            val second = m.groupValues[3].ifEmpty { "0" }.toInt()
            if (minute > 59 || second > 59) continue
            val piece = timePiece(m.range.first, m.range.last + 1, hour, minute, second, m.groupValues[4]) ?: continue
            out += piece
        }
        for (m in hourMeridiem.findAll(text)) {
            out += timePiece(m.range.first, m.range.last + 1, m.groupValues[1].toInt(), 0, 0, m.groupValues[2]) ?: continue
        }
        for (m in bareHour.findAll(text)) {
            val hour = m.groupValues[1].toInt()
            if (hour !in 1..12) continue
            out += TimePiece(m.range.first, m.range.last + 1, hour, 0, ambiguous = true)
        }
        for (m in noon.findAll(text)) {
            out += TimePiece(m.range.first, m.range.last + 1, 12, 0)
        }
    }

    private fun timePiece(start: Int, end: Int, hour: Int, minute: Int, second: Int, meridiem: String): TimePiece? {
        val marker = meridiem.lowercase().replace(".", "")
        return when {
            marker.isNotEmpty() -> {
                if (hour !in 1..12) return null
                val base = hour % 12
                TimePiece(start, end, if (marker == "pm") base + 12 else base, minute, second)
            }
            hour in 1..12 -> TimePiece(start, end, hour, minute, second, ambiguous = true)
            hour in 0..23 -> TimePiece(start, end, hour, minute, second)
            else -> null
        }
    }

    private fun collectOffsets(text: String, now: Instant, today: LocalDate, calendar: KeeprCalendar, out: MutableList<Piece>) {
        for (m in offset.findAll(text)) {
            val count = m.groupValues[1].lowercase().let { numberWords[it] ?: it.toIntOrNull() } ?: continue
            val unit = m.groupValues[2].lowercase()
            val start = m.range.first
            val end = m.range.last + 1
            out += when {
                unit.startsWith("min") -> MomentPiece(start, end, calendar.addingMinutes(count, now))
                unit.startsWith("h") -> MomentPiece(start, end, calendar.addingHours(count, now))
                unit.startsWith("day") -> DayPiece(start, end, today.plusDays(count.toLong()))
                unit.startsWith("week") -> DayPiece(start, end, today.plusWeeks(count.toLong()))
                unit.startsWith("month") -> DayPiece(start, end, today.plusMonths(count.toLong()))
                else -> DayPiece(start, end, today.plusYears(count.toLong()))
            }
        }
    }

    // MARK: - Resolving

    private fun validDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    /** An explicit year is taken as written; without one, the next occurrence from today. */
    private fun monthDayDate(month: Int, day: Int, year: String, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        if (year.isNotEmpty()) {
            val value = year.toInt().let { if (year.length == 2) 2000 + it else it }
            return validDate(value, month, day)
        }
        for (offset in 0..8) {
            val candidate = validDate(today.year + offset, month, day) ?: continue
            if (!candidate.isBefore(today)) return candidate
        }
        return null
    }

    private fun nextDayOfMonth(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        val firstOfMonth = today.withDayOfMonth(1)
        for (offset in 0L..12L) {
            val month = firstOfMonth.plusMonths(offset)
            if (day > month.lengthOfMonth()) continue
            val candidate = month.withDayOfMonth(day)
            if (!candidate.isBefore(today)) return candidate
        }
        return null
    }

    private fun weekdayDate(target: DayOfWeek, modifier: String, today: LocalDate): LocalDate {
        val ahead = ((target.value - today.dayOfWeek.value) + 7) % 7
        return when (modifier.lowercase().replace(Regex("\\s+"), " ")) {
            "this" -> today.plusDays(ahead.toLong())
            "last" -> today.minusDays((if (ahead == 0) 7 else 7 - ahead).toLong())
            else -> today.plusDays((if (ahead == 0) 7 else ahead).toLong())
        }
    }

    private fun resolveTime(time: TimePiece, part: PartOfDay?): LocalTime {
        var hour = time.hour
        if (time.ambiguous) {
            hour = when {
                part != null && part.afternoon -> if (hour < 12) hour + 12 else hour
                part != null -> if (hour == 12) 0 else hour
                hour in 1..6 -> hour + 12
                else -> hour
            }
        }
        return LocalTime.of(hour, time.minute, time.second)
    }

    // MARK: - Combining neighbors

    private val weekdayDateGap = Regex("^\\s*,?\\s*$")
    private val dayTimeGap = Regex("^\\s*,?\\s*(?:(?:by|from|around)\\s+)?$", RegexOption.IGNORE_CASE)
    private val timeDayGap = Regex("^\\s*,?\\s*$")
    private val tightGap = Regex("^\\s*$")

    private fun combine(text: String, pieces: List<Piece>, today: LocalDate, calendar: KeeprCalendar): List<DetectedDate> {
        val out = mutableListOf<DetectedDate>()
        fun gap(a: Piece, b: Piece?, pattern: Regex): Boolean =
            b != null && b.start >= a.end && pattern.matches(text.substring(a.end, b.start))
        fun at(day: LocalDate, time: LocalTime): Instant = day.atTime(time).atZone(calendar.zone).toInstant()

        var i = 0
        while (i < pieces.size) {
            when (val piece = pieces[i]) {
                is DayPiece -> {
                    var day = piece
                    val following = pieces.getOrNull(i + 1)
                    // "Friday, June 20": the explicit date is the one that counts.
                    if (day.isWeekday && following is DayPiece && !following.isWeekday && gap(day, following, weekdayDateGap)) {
                        day = following.copy(start = day.start)
                        i++
                    }
                    var end = day.end
                    var defaultTime = day.defaultTime
                    var part = day.part
                    val partPiece = pieces.getOrNull(i + 1)
                    if (partPiece is PartPiece && gap(day, partPiece, tightGap)) {
                        part = partPiece.part
                        defaultTime = partPiece.part.time
                        end = partPiece.end
                        i++
                    }
                    val timePiece = pieces.getOrNull(i + 1)
                    val anchor = pieces[i]
                    if (timePiece is TimePiece && gap(anchor, timePiece, dayTimeGap)) {
                        i++
                        var timeEnd = timePiece.end
                        val trailing = pieces.getOrNull(i + 1)
                        if (trailing is PartPiece && gap(timePiece, trailing, tightGap)) {
                            part = trailing.part
                            timeEnd = trailing.end
                            i++
                        }
                        out += DetectedDate(day.start until timeEnd, at(day.day, resolveTime(timePiece, part)), hasTime = true)
                    } else {
                        out += DetectedDate(day.start until end, at(day.day, defaultTime), hasTime = false)
                    }
                }
                is TimePiece -> {
                    var end = piece.end
                    var part: PartOfDay? = null
                    var anchor: Piece = piece
                    val partPiece = pieces.getOrNull(i + 1)
                    if (partPiece is PartPiece && gap(piece, partPiece, tightGap)) {
                        part = partPiece.part
                        end = partPiece.end
                        anchor = partPiece
                        i++
                    }
                    val dayPiece = pieces.getOrNull(i + 1)
                    if (dayPiece is DayPiece && gap(anchor, dayPiece, timeDayGap)) {
                        var day = dayPiece
                        i++
                        val explicit = pieces.getOrNull(i + 1)
                        if (day.isWeekday && explicit is DayPiece && !explicit.isWeekday && gap(day, explicit, weekdayDateGap)) {
                            day = explicit
                            i++
                        }
                        out += DetectedDate(piece.start until day.end, at(day.day, resolveTime(piece, part ?: day.part)), hasTime = true)
                    } else {
                        out += DetectedDate(piece.start until end, at(today, resolveTime(piece, part)), hasTime = true)
                    }
                }
                is MomentPiece -> out += DetectedDate(piece.start until piece.end, piece.instant, hasTime = true)
                // "in the morning" with no day or hour beside it isn't a date.
                is PartPiece -> Unit
            }
            i++
        }
        return out
    }
}
