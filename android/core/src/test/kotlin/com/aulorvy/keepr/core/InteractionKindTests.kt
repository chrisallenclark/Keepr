package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.model.InteractionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Translated from KeeprTests/InteractionKindTests.swift ("Interaction kinds").
//
// Kinds are stored as raw strings, so the list can grow — but a raw value that
// changes silently reclassifies interactions people already logged. These pin
// the wire format and check the list stays usable.
class InteractionKindTests {

    /** Raw values are stable, so already-logged interactions keep their kind */
    @Test
    fun rawValuesAreStable() {
        val expected = setOf(
            "inPerson", "call", "video", "text", "dm",
            "email", "meeting", "meal", "event", "other",
        )
        assertEquals(expected, InteractionKind.entries.map { it.rawValue }.toSet())
    }

    /** An unknown raw value doesn't exist, so decoding falls back rather than crashing */
    @Test
    fun unknownRawValuesAreNil() {
        assertNull(InteractionKind.from("carrierPigeon"))
    }

    /** Every kind is presentable */
    @Test
    fun everyKindHasWordsAndAnIcon() {
        for (kind in InteractionKind.entries) {
            assertTrue(kind.title.isNotEmpty())
            assertTrue(kind.symbolName.isNotEmpty())
        }
    }

    /** Kinds cover how people actually reach each other, without becoming a wall */
    @Test
    fun theListIsBroadButBounded() {
        // Both halves matter. Too few and someone's normal week has no home;
        // too many and picking one costs more than the precision is worth.
        assertTrue(InteractionKind.entries.size >= 8)
        assertTrue(InteractionKind.entries.size <= 12)

        val titles = InteractionKind.entries.map { it.title }.toSet()
        for (expected in listOf("Video Call", "DM", "Coffee or Meal", "Event")) {
            assertTrue(titles.contains(expected), "$expected is a normal way business gets done")
        }
    }

    /** Names are distinct, so the picker never shows the same word twice */
    @Test
    fun titlesAreUnique() {
        assertEquals(InteractionKind.entries.size, InteractionKind.entries.map { it.title }.toSet().size)
    }
}
