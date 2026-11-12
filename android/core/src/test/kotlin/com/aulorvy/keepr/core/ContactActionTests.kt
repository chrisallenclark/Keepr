package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.domain.RelativeDate
import com.aulorvy.keepr.core.services.CommunicationLauncher
import com.aulorvy.keepr.core.services.ContactMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

// Translated from KeeprTests/ContactActionTests.swift, which holds two suites.

/** "Reaching people" */
class CommunicationLauncherTests {

    /** Formatted numbers are reduced to something tel: and sms: accept */
    @Test
    fun dialableStripsFormatting() {
        assertEquals("5615550142", CommunicationLauncher.dialable("(561) 555-0142"))
        assertEquals("+15615550142", CommunicationLauncher.dialable("+1 561-555-0142"))
        assertEquals("5550142", CommunicationLauncher.dialable("555.0142 ext 2"))
    }

    /** Each method builds the right system URL */
    @Test
    fun urlsPerMethod() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake", family = "Martinez")
        person.phoneNumbers = listOf("(561) 555-0142")
        person.emailAddresses = listOf("jake@example.com")

        assertEquals("tel:5615550142", CommunicationLauncher.url(ContactMethod.CALL, person))
        assertEquals("sms:5615550142", CommunicationLauncher.url(ContactMethod.MESSAGE, person))
        assertEquals("mailto:jake@example.com", CommunicationLauncher.url(ContactMethod.EMAIL, person))
    }

    /** Missing details produce no URL and no availability, rather than a dead button */
    @Test
    fun unavailableMethods() {
        val store = TestStore()
        val person = Make.person(store.context, given = "No", family = "Details")

        for (method in ContactMethod.entries) {
            assertNull(CommunicationLauncher.url(method, person))
            assertFalse(CommunicationLauncher.isAvailable(method, person))
        }
    }
}

/** "Relative dates" */
class RelativeDateTests {

    private val calendar = TestDates.calendar
    private val now = TestDates.now

    /** Past phrasing */
    @Test
    fun pastPhrasing() {
        assertEquals("Today", RelativeDate.past(TestDates.days(0), now, calendar))
        assertEquals("Yesterday", RelativeDate.past(TestDates.days(-1), now, calendar))
        assertEquals("3 days ago", RelativeDate.past(TestDates.days(-3), now, calendar))
    }

    /** Due phrasing */
    @Test
    fun duePhrasing() {
        assertEquals("Today", RelativeDate.due(TestDates.days(0), now, calendar))
        assertEquals("Tomorrow", RelativeDate.due(TestDates.days(1), now, calendar))
        assertEquals("Yesterday", RelativeDate.due(TestDates.days(-1), now, calendar))
        assertEquals("4 days ago", RelativeDate.due(TestDates.days(-4), now, calendar))
    }

    /** A person with nothing logged says so instead of showing a date */
    @Test
    fun lastContactWithNoInteractions() {
        assertEquals("No interactions logged", RelativeDate.lastContact(null, now, calendar))
    }
}
