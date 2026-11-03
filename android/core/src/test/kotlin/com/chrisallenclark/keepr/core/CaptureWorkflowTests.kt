package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.FollowUpDraft
import com.chrisallenclark.keepr.core.model.Interaction
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.MemoryCategory
import com.chrisallenclark.keepr.core.model.MemoryDraft
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.Priority
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipStatus
import com.chrisallenclark.keepr.core.services.BrainDumpWorkflow
import com.chrisallenclark.keepr.core.services.CaptureDraft
import com.chrisallenclark.keepr.core.services.CaptureSubject
import com.chrisallenclark.keepr.core.services.HeuristicCaptureExtractor
import com.chrisallenclark.keepr.core.services.QuickCaptureWorkflow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// The workflow behind QuickCaptureView, PersonPicker and BrainDumpView: who a
// capture is filed under, and exactly what saving writes.
class CaptureWorkflowTests {

    private val calendar = TestDates.calendar
    private val now = TestDates.now

    // MARK: - Who it's about

    @Test
    fun suggestedNameMatchesAVisiblePersonByFirstName() {
        val store = TestStore()
        val jake = Make.person(store.context, given = "Jake", family = "Martinez", relationship = RelationshipContext.BUSINESS)
        val subject = QuickCaptureWorkflow.matchSuggestedPerson("jake", store.fetch(), ContextMode.BUSINESS)
        assertSame(jake, subject.person)
        assertEquals("", subject.newPersonName)
        assertTrue(subject.isChosen)
    }

    @Test
    fun suggestedNameMatchesFullAndPreferredNames() {
        val store = TestStore()
        val sam = Make.person(store.context, given = "Samantha", family = "Lee", preferred = "Sam", relationship = RelationshipContext.BOTH)
        assertSame(sam, QuickCaptureWorkflow.matchSuggestedPerson("Samantha Lee", store.fetch(), ContextMode.PERSONAL).person)
        assertSame(sam, QuickCaptureWorkflow.matchSuggestedPerson("SAM", store.fetch(), ContextMode.BUSINESS).person)
    }

    @Test
    fun suggestedNameOutsideTheModeOffersANewPerson() {
        val store = TestStore()
        Make.person(store.context, given = "Jake", family = "Martinez", relationship = RelationshipContext.PERSONAL)
        Make.person(store.context, given = "Dana", family = "Old", relationship = RelationshipContext.BUSINESS, status = RelationshipStatus.ARCHIVED)
        val jake = QuickCaptureWorkflow.matchSuggestedPerson("Jake", store.fetch(), ContextMode.BUSINESS)
        assertNull(jake.person)
        assertEquals("Jake", jake.newPersonName)
        // Archived people aren't preselected either.
        assertNull(QuickCaptureWorkflow.matchSuggestedPerson("Dana", store.fetch(), ContextMode.BUSINESS).person)
    }

    @Test
    fun noSuggestedNameChoosesNobody() {
        val store = TestStore()
        Make.person(store.context, given = "Jake")
        val subject = QuickCaptureWorkflow.matchSuggestedPerson(null, store.fetch(), ContextMode.PERSONAL)
        assertNull(subject.person)
        assertFalse(subject.isChosen)
        assertFalse(CaptureSubject(newPersonName = "   ").isChosen)
    }

    // MARK: - Picker

    @Test
    fun pickerListsEveryoneMostRecentFirstAndSearchesAcrossModes() {
        val store = TestStore()
        val old = Make.person(store.context, given = "Ann", family = "Zed", relationship = RelationshipContext.BUSINESS, lastInteractionAt = TestDates.days(-30))
        val recent = Make.person(store.context, given = "Bob", family = "Young", relationship = RelationshipContext.PERSONAL, lastInteractionAt = TestDates.days(-1))
        val never = Make.person(store.context, given = "Cy", family = "Able")
        assertEquals(listOf(recent, old, never), QuickCaptureWorkflow.pickerResults(store.fetch(), ""))
        assertEquals(listOf(recent), QuickCaptureWorkflow.pickerResults(store.fetch(), " bob "))
    }

    @Test
    fun pickerOffersToAddTheQueryOrTheSpottedName() {
        val store = TestStore()
        val jake = Make.person(store.context, given = "Jake", family = "Martinez")
        assertEquals("Priya", QuickCaptureWorkflow.proposedNewName("", "Priya"))
        assertEquals("Jo", QuickCaptureWorkflow.proposedNewName("  Jo ", "Priya"))
        assertEquals("", QuickCaptureWorkflow.proposedNewName("", null))
        assertTrue(QuickCaptureWorkflow.offersNewPerson("Priya", listOf(jake)))
        assertFalse(QuickCaptureWorkflow.offersNewPerson("jake martinez", listOf(jake)))
        assertFalse(QuickCaptureWorkflow.offersNewPerson("", listOf(jake)))
    }

    @Test
    fun newPersonSplitsTheNameAndTakesTheMode() {
        val store = TestStore()
        val person = QuickCaptureWorkflow.createPerson("  Mary  Ann Smith ", ContextMode.BUSINESS, store.context, now)
        assertNotNull(person)
        assertEquals("Mary", person.givenName)
        assertEquals("Ann Smith", person.familyName)
        assertEquals(RelationshipContext.BUSINESS, person.context)
        assertEquals(1, store.fetch<Person>().size)
        assertNull(QuickCaptureWorkflow.createPerson(" ", ContextMode.PERSONAL, store.context, now))
    }

    // MARK: - Saving a quick capture

    @Test
    fun savingFilesEverythingUnderAnExistingPerson() = runTest {
        val store = TestStore()
        val jake = Make.person(store.context, given = "Jake", family = "Martinez", relationship = RelationshipContext.BUSINESS, lastInteractionAt = TestDates.days(-10))
        jake.awaitingReplySince = TestDates.days(-3)
        val text = "Talked to Jake at Life Time. He owns a roofing company, wants to lose 20 pounds and said to text him next week about training."
        val draft = HeuristicCaptureExtractor().extract(text, now)
        val subject = QuickCaptureWorkflow.matchSuggestedPerson(draft.suggestedName, store.fetch(), ContextMode.BUSINESS)
        assertSame(jake, subject.person)

        val result = QuickCaptureWorkflow.save(draft, "  $text\n", subject, ContextMode.BUSINESS, store.context, now, calendar)
        assertNotNull(result)

        val interaction = store.fetch<Interaction>().single()
        assertTrue(interaction.isQuickCapture)
        assertEquals(text, interaction.rawNote)
        assertSame(jake, interaction.person)
        assertEquals(draft.memories.size, store.fetch<Memory>().size)
        assertTrue(store.fetch<Memory>().all { it.person === jake && it.sourceInteraction === interaction })

        val followUp = store.fetch<FollowUp>().single()
        assertSame(followUp, result.followUp)
        assertSame(jake, followUp.person)
        assertSame(interaction, followUp.sourceInteraction)
        assertEquals(calendar.startOfDay(followUp.dueDate), followUp.dueDate)
        assertFalse(followUp.hasTime)

        assertEquals(draft.occurredAt, jake.lastInteractionAt)
        assertNull(jake.awaitingReplySince)
        assertEquals(1, store.fetch<Person>().size)
    }

    @Test
    fun switchedOffSuggestionsAreNotSaved() {
        val store = TestStore()
        val person = Make.person(store.context)
        val draft = CaptureDraft(
            summary = "",
            kind = InteractionKind.CALL,
            occurredAt = TestDates.days(-1),
            memories = listOf(
                MemoryDraft("Keep me", MemoryCategory.WORK),
                MemoryDraft("Drop me", MemoryCategory.FAMILY, isSelected = false),
            ),
            followUp = FollowUpDraft("Call back", TestDates.days(3), isSelected = false),
        )
        val result = QuickCaptureWorkflow.save(draft, "note", CaptureSubject(person), ContextMode.PERSONAL, store.context, now, calendar)
        assertNotNull(result)
        assertEquals(listOf("Keep me"), store.fetch<Memory>().map { it.content })
        assertTrue(store.fetch<FollowUp>().isEmpty())
        assertNull(result.followUp)
        val interaction = store.fetch<Interaction>().single()
        assertEquals(InteractionKind.CALL, interaction.kind)
        assertNull(interaction.summary)
    }

    @Test
    fun aBlankFollowUpTitleIsSkipped() {
        val store = TestStore()
        val person = Make.person(store.context)
        val draft = CaptureDraft(occurredAt = now, followUp = FollowUpDraft("   ", TestDates.days(3), priority = Priority.HIGH))
        QuickCaptureWorkflow.save(draft, "note", CaptureSubject(person), ContextMode.PERSONAL, store.context, now, calendar)
        assertTrue(store.fetch<FollowUp>().isEmpty())
    }

    @Test
    fun anOlderCaptureDoesNotRewindLastContact() {
        val store = TestStore()
        val person = Make.person(store.context, lastInteractionAt = TestDates.days(-1))
        val draft = CaptureDraft(occurredAt = TestDates.days(-5))
        QuickCaptureWorkflow.save(draft, "note", CaptureSubject(person), ContextMode.PERSONAL, store.context, now, calendar)
        assertEquals(TestDates.days(-1), person.lastInteractionAt)
    }

    @Test
    fun savingForANewNameCreatesThemInTheCurrentMode() {
        val store = TestStore()
        val draft = CaptureDraft(occurredAt = now, memories = listOf(MemoryDraft("Owns a roofing company", MemoryCategory.WORK)))
        val result = QuickCaptureWorkflow.save(draft, "note", CaptureSubject(newPersonName = "Jake Martinez"), ContextMode.BUSINESS, store.context, now, calendar)
        assertNotNull(result)
        val person = store.fetch<Person>().single()
        assertEquals("Jake", person.givenName)
        assertEquals("Martinez", person.familyName)
        assertEquals(RelationshipContext.BUSINESS, person.context)
        assertSame(person, store.fetch<Memory>().single().person)
    }

    @Test
    fun savingWithNobodyChosenWritesNothing() {
        val store = TestStore()
        val result = QuickCaptureWorkflow.save(CaptureDraft(occurredAt = now), "note", CaptureSubject(), ContextMode.PERSONAL, store.context, now, calendar)
        assertNull(result)
        assertTrue(store.context.all().isEmpty())
    }

    // MARK: - Brain dump

    @Test
    fun brainDumpSavesKeptNonBlankFactsOnly() {
        val store = TestStore()
        val person = Make.person(store.context)
        val drafts = listOf(
            MemoryDraft("  Two kids, Mia and Leo ", MemoryCategory.FAMILY, Priority.HIGH),
            MemoryDraft("Hates mornings", MemoryCategory.PREFERENCES, isSelected = false),
            MemoryDraft("   "),
        )
        val saved = BrainDumpWorkflow.save(person, drafts, alsoLogInteraction = false, context = store.context, now = now)
        assertEquals(1, saved.size)
        val memory = store.fetch<Memory>().single()
        assertEquals("Two kids, Mia and Leo", memory.content)
        assertEquals(MemoryCategory.FAMILY, memory.category)
        assertEquals(Priority.HIGH, memory.importance)
        assertNull(memory.sourceInteraction)
        assertTrue(store.fetch<Interaction>().isEmpty())
        assertNull(person.lastInteractionAt)
    }

    @Test
    fun brainDumpCanAlsoLogAnInteraction() {
        val store = TestStore()
        val person = Make.person(store.context, lastInteractionAt = TestDates.days(-40))
        person.awaitingReplySince = TestDates.days(-2)
        BrainDumpWorkflow.save(person, listOf(MemoryDraft("Runs marathons", MemoryCategory.INTERESTS)), alsoLogInteraction = true, context = store.context, now = now)
        val interaction = store.fetch<Interaction>().single()
        assertEquals(InteractionKind.IN_PERSON, interaction.kind)
        assertEquals(BrainDumpWorkflow.INTERACTION_SUMMARY, interaction.summary)
        assertFalse(interaction.isQuickCapture)
        assertSame(interaction, store.fetch<Memory>().single().sourceInteraction)
        assertEquals(now, person.lastInteractionAt)
        assertNull(person.awaitingReplySince)
    }

    @Test
    fun keepAllAndKeepNone() {
        val mixed = listOf(MemoryDraft("a"), MemoryDraft("b", isSelected = false))
        assertEquals(1, BrainDumpWorkflow.keptCount(mixed))
        val all = BrainDumpWorkflow.toggleAll(mixed)
        assertTrue(all.all { it.isSelected })
        val none = BrainDumpWorkflow.toggleAll(all)
        assertTrue(none.none { it.isSelected })
        assertEquals(0, BrainDumpWorkflow.keptCount(none))
    }
}
