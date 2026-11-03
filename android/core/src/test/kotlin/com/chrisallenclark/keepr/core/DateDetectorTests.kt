package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.services.DateDetector
import com.chrisallenclark.keepr.core.services.DetectedDate
import com.chrisallenclark.keepr.core.services.HeuristicCaptureExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Android-only: [DateDetector] replaces `NSDataDetector`, which the iOS suite
 * never had to test. "Now" is Sunday 15 June 2025, 12:00 UTC.
 */
class DateDetectorTests {

    private val now = TestDates.now
    private val calendar = TestDates.calendar

    private fun detect(text: String): List<DetectedDate> = DateDetector.matches(text, now, calendar)

    private fun single(text: String): DetectedDate {
        val found = detect(text)
        assertEquals(1, found.size, "expected one date in \"$text\", got $found")
        return found.single()
    }

    private fun assertDay(text: String, year: Int, month: Int, day: Int) {
        val found = single(text)
        assertEquals(TestDates.date(year, month, day, 12), found.date, text)
        assertEquals(false, found.hasTime, text)
    }

    private fun assertTime(text: String, year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) {
        val found = single(text)
        assertEquals(TestDates.date(year, month, day, hour, minute), found.date, text)
        assertEquals(true, found.hasTime, text)
    }

    @Test
    fun relativeDays() {
        assertDay("call her today", 2025, 6, 15)
        assertDay("call her tomorrow", 2025, 6, 16)
        assertDay("saw him yesterday", 2025, 6, 14)
        assertDay("the day after tomorrow works", 2025, 6, 17)
    }

    @Test
    fun weekdays() {
        assertDay("see him Tuesday", 2025, 6, 17)
        assertDay("see him next Tuesday", 2025, 6, 17)
        assertDay("see him on Saturday", 2025, 6, 21)
        assertDay("this Sunday", 2025, 6, 15)
        assertDay("Sunday", 2025, 6, 22)
        assertDay("last friday", 2025, 6, 13)
        assertDay("next thurs.", 2025, 6, 19)
    }

    @Test
    fun monthNameDates() {
        assertDay("June 20", 2025, 6, 20)
        assertDay("on Jun. 20th", 2025, 6, 20)
        assertDay("June 15", 2025, 6, 15)
        assertDay("Birthday is March 14", 2026, 3, 14)
        assertDay("June 20, 2026", 2026, 6, 20)
        assertDay("the 20th of June", 2025, 6, 20)
        assertDay("4 July", 2025, 7, 4)
        assertDay("May 5", 2026, 5, 5)
        assertDay("Feb 29", 2028, 2, 29)
    }

    @Test
    fun numericDates() {
        assertDay("due 6/20", 2025, 6, 20)
        assertDay("due 6/20/26", 2026, 6, 20)
        assertDay("due 1/5/2026", 2026, 1, 5)
        assertDay("2025-07-01", 2025, 7, 1)
        assertTrue(detect("2/30").isEmpty())
        assertTrue(detect("open 24/7").isEmpty())
    }

    @Test
    fun ordinalDays() {
        assertDay("on the 14th", 2025, 7, 14)
        assertDay("the 20th", 2025, 6, 20)
        assertDay("on the 31st", 2025, 7, 31)
    }

    @Test
    fun times() {
        assertTime("at 3pm", 2025, 6, 15, 15)
        assertTime("at 3:30", 2025, 6, 15, 15, 30)
        assertTime("around 9:15 a.m.", 2025, 6, 15, 9, 15)
        assertTime("17:45", 2025, 6, 15, 17, 45)
        assertTime("at noon", 2025, 6, 15, 12)
        assertTime("tomorrow at 3pm", 2025, 6, 16, 15)
        assertTime("3pm on Friday", 2025, 6, 20, 15)
        assertTime("Friday at 9", 2025, 6, 20, 9)
        assertTime("June 20 at 5:30pm", 2025, 6, 20, 17, 30)
        assertTime("tonight at 8", 2025, 6, 15, 20)
        assertTime("at 8 tomorrow", 2025, 6, 16, 8)
        assertTime("Friday at 7 in the evening", 2025, 6, 20, 19)
    }

    @Test
    fun partsOfDayAndOffsets() {
        assertEquals(TestDates.date(2025, 6, 16, 9), single("tomorrow morning").date)
        assertEquals(TestDates.date(2025, 6, 20, 19), single("Friday evening").date)
        assertEquals(TestDates.date(2025, 6, 15, 20), single("tonight").date)
        assertDay("in 3 days", 2025, 6, 18)
        assertDay("in two weeks", 2025, 6, 29)
        assertTime("in 2 hours", 2025, 6, 15, 14)
    }

    @Test
    fun explicitDateBeatsItsWeekday() {
        assertDay("Friday, June 27", 2025, 6, 27)
    }

    @Test
    fun proseIsNotADate() {
        for (text in listOf(
            "sat down with him", "he may come by", "lost 20 pounds", "Two kids, 7 and 10",
            "Talked to Jake at Life Time.", "Told him something about it", "he's 5 foot 10",
            "text him about training", "met at 24 Hour Fitness",
        )) {
            assertTrue(detect(text).isEmpty(), "\"$text\" found ${detect(text)}")
        }
    }

    @Test
    fun dueDatesUseDetectedDates() {
        assertEquals(TestDates.dayStart(5), HeuristicCaptureExtractor.dueDate("call him Friday", now, calendar))
        assertEquals(TestDates.dayStart(0), HeuristicCaptureExtractor.dueDate("call at 3pm", now, calendar))
        // Today at noon is not after 12:00 now, so it doesn't count as a deadline.
        assertNull(HeuristicCaptureExtractor.dueDate("call her today", now, calendar))
        // The earliest future date wins.
        assertEquals(TestDates.dayStart(2), HeuristicCaptureExtractor.dueDate("June 20 or maybe Tuesday", now, calendar))
    }
}
