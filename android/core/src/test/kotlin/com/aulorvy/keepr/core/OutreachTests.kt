package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.domain.CadenceEngine
import com.aulorvy.keepr.core.domain.Outreach
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.RelationshipContext
import com.aulorvy.keepr.core.model.RelationshipStatus
import com.aulorvy.keepr.core.model.RelationshipTag
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Translated from KeeprTests/OutreachTests.swift ("Reaching out").
//
// "I texted them and heard nothing" is a real state, and the app gets it wrong
// in both directions if it's collapsed into logging a conversation.
class OutreachTests {

    private fun person(store: TestStore, lastInteraction: Instant? = null): Person =
        Make.person(
            store.context,
            given = "Stan",
            family = "Ley",
            relationship = RelationshipContext.BUSINESS,
            createdAt = TestDates.days(-200),
            lastInteractionAt = lastInteraction,
        )

    private fun clientTag(store: TestStore, days: Int): RelationshipTag {
        val tag = Make.tag(store.context, name = "Current Client")
        tag.cadenceDays = days
        return tag
    }

    /** Reaching out counts as your part done, so the rhythm resets */
    @Test
    fun outreachResetsTheRhythm() {
        val store = TestStore()
        val subject = person(store, lastInteraction = TestDates.days(-45))
        Make.attach(clientTag(store, days = 30), to = subject)
        assertEquals(1, CadenceEngine.due(listOf(subject), now = TestDates.now, calendar = TestDates.calendar).size)

        Outreach.markReachedOut(subject, at = TestDates.now, context = store.context)

        assertTrue(CadenceEngine.due(listOf(subject), now = TestDates.now, calendar = TestDates.calendar).isEmpty())
    }

    /** But it's recorded as an attempt, not a conversation */
    @Test
    fun outreachIsMarkedAsAwaiting() {
        val store = TestStore()
        val subject = person(store)

        val interaction = Outreach.markReachedOut(subject, at = TestDates.now, context = store.context)
        store.save()

        assertTrue(interaction.awaitingReply)
        assertEquals(TestDates.now, subject.awaitingReplySince)
        assertEquals(TestDates.now, subject.lastInteractionAt)
    }

    /** They show up as waiting, longest wait first */
    @Test
    fun waitingListIsOrdered() {
        val store = TestStore()
        val recent = Make.person(store.context, given = "Recent", family = "Person")
        val old = Make.person(store.context, given = "Old", family = "Person")
        Outreach.markReachedOut(recent, at = TestDates.days(-1), context = store.context)
        Outreach.markReachedOut(old, at = TestDates.days(-9), context = store.context)

        val waiting = Outreach.waiting(listOf(recent, old), now = TestDates.now, calendar = TestDates.calendar)

        assertEquals(listOf("Old", "Recent"), waiting.map { it.person.givenName })
        assertEquals(9, waiting.firstOrNull()?.days)
        assertEquals("Sent 9 days ago", waiting.firstOrNull()?.summary)
        assertEquals("Sent yesterday", waiting.lastOrNull()?.summary)
    }

    /** Once they reply, the waiting stops without inventing a conversation */
    @Test
    fun replyingClearsTheWait() {
        val store = TestStore()
        val subject = person(store)
        Outreach.markReachedOut(subject, at = TestDates.days(-3), context = store.context)

        Outreach.markReplied(subject)

        assertNull(subject.awaitingReplySince)
        assertTrue(Outreach.waiting(listOf(subject), now = TestDates.now).isEmpty())
        assertEquals(1, subject.interactionList.size, "no phantom interaction is added")
    }

    /** Archived people don't sit on the waiting list forever */
    @Test
    fun archivedPeopleDropOff() {
        val store = TestStore()
        val subject = person(store)
        Outreach.markReachedOut(subject, at = TestDates.days(-3), context = store.context)
        subject.status = RelationshipStatus.ARCHIVED

        assertTrue(Outreach.waiting(listOf(subject), now = TestDates.now).isEmpty())
    }

    /** Someone you never chased isn't waiting on anything */
    @Test
    fun noOutreachMeansNoWait() {
        val store = TestStore()
        val subject = person(store, lastInteraction = TestDates.days(-2))

        assertTrue(Outreach.waiting(listOf(subject), now = TestDates.now).isEmpty())
    }
}
