package com.aulorvy.keepr.core

import com.aulorvy.keepr.core.domain.NoteTitler
import com.aulorvy.keepr.core.domain.PeopleEngine
import com.aulorvy.keepr.core.domain.SearchField
import com.aulorvy.keepr.core.model.ConversationNote
import com.aulorvy.keepr.core.model.InteractionKind
import com.aulorvy.keepr.core.model.NoteSource
import com.aulorvy.keepr.core.model.PersonFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Translated from KeeprTests/ConversationNoteTests.swift ("Note titles" and
// "Conversation notes").

/**
 * Titles are what make a pile of pasted write-ups usable a year later, and
 * they're the one thing the user won't type. So the guess has to be right
 * often and never embarrassing.
 */
class NoteTitlerTests {

    // MARK: - An existing heading wins

    /** A heading at the top is the title */
    @Test
    fun headingIsUsedVerbatim() {
        val text = """
            Consultation — Jake Mathers
            Goals: drop 20 lb before the wedding in June.
        """.trimIndent()

        assertEquals("Consultation — Jake Mathers", NoteTitler.suggest(text))
    }

    /** Markdown and bullet decoration is stripped off a heading */
    @Test
    fun headingDecorationIsRemoved() {
        assertEquals("Quarterly check-in", NoteTitler.heading("## Quarterly check-in\nNumbers were good."))
        assertEquals("Pricing conversation", NoteTitler.heading("• Pricing conversation\nHe pushed back."))
    }

    /** A sentence is not a heading, however short */
    @Test
    fun prosePassesThrough() {
        assertNull(NoteTitler.heading("He wants to start in January."))
        assertNull(NoteTitler.heading("Jake called about the roof, and I said I'd check."))
    }

    /** A bare document label is not a title */
    @Test
    fun documentLabelsAreRejected() {
        assertNull(NoteTitler.heading("Summary\nHe owns a roofing company."))
        assertNull(NoteTitler.heading("Meeting Notes\nWe talked pricing."))
        assertNull(NoteTitler.heading("Action Items\nSend the quote."))
    }

    /** A one-word first line is a label, not a heading */
    @Test
    fun singleWordIsNotAHeading() {
        assertNull(NoteTitler.heading("Transcript\nSo how have you been?"))
    }

    /** A very long first line is prose, not a heading */
    @Test
    fun longFirstLineIsNotAHeading() {
        val long = "word ".repeat(30)
        assertNull(NoteTitler.heading(long + "\nmore"))
    }

    // MARK: - Falling back to what kind of conversation it was

    /** With no heading, the kind of conversation plus the date is the title */
    @Test
    fun kindAndDateIsUsed() {
        val text = "Ran the initial consultation with him today. He wants to drop 20 lb."

        val title = NoteTitler.suggest(text, TestDates.now, TestDates.calendar)
        assertTrue(title.startsWith("Consultation · "))
    }

    /** The most specific kind wins over a vaguer one later on */
    @Test
    fun specificKindWins() {
        val text = "Did his consultation this morning, then a quick meeting with his wife."

        assertEquals("Consultation", NoteTitler.kind(text))
    }

    /** A kind mentioned far down the page doesn't retitle the note */
    @Test
    fun distantKeywordsAreIgnored() {
        val text = "He pushed back hard on the number. " +
            "Filler sentence here. ".repeat(40) +
            "We should book a consultation."

        assertNull(NoteTitler.kind(text))
    }

    // MARK: - Falling back to the opening line

    /** With nothing else to go on, the opening sentence is the title */
    @Test
    fun openingSentenceIsUsed() {
        val text = "He pushed back hard on the number and wants to think about it."

        val title = NoteTitler.suggest(text)
        assertTrue(title.startsWith("He pushed back hard"))
        assertFalse(title.contains("·"), "this isn't a kind-and-date title")
    }

    /** A long opening sentence is cut at a word, not mid-word */
    @Test
    fun openingSentenceIsTruncatedCleanly() {
        val text = "He pushed back hard on the number because his partner wants " +
            "to see the whole year budgeted before they commit to anything."

        val title = assertNotNull(NoteTitler.openingSentence(text))
        assertTrue(title.endsWith("…"))
        assertTrue(title.codePointCount(0, title.length) <= 54)
        assertFalse(title.contains("  "))
    }

    /** Empty text suggests nothing rather than a guess */
    @Test
    fun emptyTextHasNoTitle() {
        assertEquals("", NoteTitler.suggest(""))
        assertEquals("", NoteTitler.suggest("   \n  "))
    }

    /** A note with only screenshots still gets a readable name */
    @Test
    fun imageOnlyNotesAreNamed() {
        assertTrue(NoteTitler.imageOnlyTitle(TestDates.now, TestDates.calendar).startsWith("Notes · "))
    }

    /** Every note source has a real symbol and a title */
    @Test
    fun sourcesAreDescribed() {
        for (source in NoteSource.entries) {
            assertTrue(AndroidSymbols.exists(source.symbolName), "${source.symbolName} missing")
            assertFalse(source.title.isEmpty())
        }
    }
}

/**
 * The record itself: how notes sort, what a collapsed card shows, and what
 * happens to the pieces when something is deleted.
 */
class ConversationNoteTests {

    /** Pinned notes lead, then the most recent */
    @Test
    fun pinnedFirstThenRecent() {
        val store = TestStore()
        val person = Make.person(store.context)
        Make.note(store.context, title = "Old", occurredAt = TestDates.days(-90), person = person)
        Make.note(store.context, title = "Newest", occurredAt = TestDates.days(-1), person = person)
        Make.note(
            store.context,
            title = "The consultation",
            occurredAt = TestDates.days(-60),
            isPinned = true,
            person = person,
        )

        assertEquals(listOf("The consultation", "Newest", "Old"), person.noteList.map { it.title })
    }

    /** A collapsed card shows a flattened preview, not blank lines */
    @Test
    fun previewFlattensLineBreaks() {
        val store = TestStore()
        val note = Make.note(
            store.context,
            title = "Consultation",
            body = "Goals\n\n- Drop 20 lb\n\n- Bench 225",
        )

        assertEquals("Goals · - Drop 20 lb · - Bench 225", note.preview)
        assertFalse(note.preview.contains("\n"))
    }

    /** A long preview is cut at a word boundary */
    @Test
    fun previewIsTruncated() {
        val store = TestStore()
        val note = Make.note(
            store.context,
            title = "Long one",
            body = "something worth remembering ".repeat(40),
        )

        assertTrue(note.preview.endsWith("…"))
        assertTrue(note.preview.codePointCount(0, note.preview.length) <= 182)
    }

    /** The card says how much is behind it */
    @Test
    fun lengthLabelCountsWords() {
        val store = TestStore()
        val note = Make.note(store.context, title = "Notes", body = "one two three")

        assertEquals("3 words", note.lengthLabel)
    }

    /** A note of screenshots reports the images instead of a word count */
    @Test
    fun lengthLabelHandlesImageOnlyNotes() {
        val store = TestStore()
        val person = Make.person(store.context)
        val note = Make.note(store.context, title = "Screens", body = "", person = person)
        Make.file(store.context, title = "Shot", person = person, conversationNote = note)

        assertFalse(note.hasBody)
        assertEquals("1 image", note.lengthLabel)
    }

    /** A note with both says so */
    @Test
    fun lengthLabelCombines() {
        val store = TestStore()
        val person = Make.person(store.context)
        val note = Make.note(store.context, title = "Both", body = "one two", person = person)
        Make.file(store.context, title = "A", person = person, conversationNote = note)
        Make.file(store.context, title = "B", person = person, conversationNote = note)

        assertEquals("2 words · 2 images", note.lengthLabel)
    }

    /** A note's screenshots don't also show up loose in Files */
    @Test
    fun noteImagesAreNotLooseFiles() {
        val store = TestStore()
        val person = Make.person(store.context)
        val note = Make.note(store.context, title = "Summary", person = person)
        Make.file(store.context, title = "Screenshot", person = person, conversationNote = note)
        Make.file(store.context, title = "InBody scan", person = person)

        assertEquals(listOf("InBody scan"), person.fileList.map { it.title })
        assertEquals(1, note.fileList.size)
    }

    /** Deleting a note takes its screenshots with it */
    @Test
    fun deletingANoteRemovesItsImages() {
        val store = TestStore()
        val person = Make.person(store.context)
        val note = Make.note(store.context, title = "Summary", person = person)
        Make.file(store.context, title = "Screenshot", person = person, conversationNote = note)
        store.context.save()

        store.context.delete(note)
        store.context.save()

        val files = store.context.fetch<PersonFile>()
        assertTrue(files.isEmpty())
    }

    /** Deleting someone removes their notes */
    @Test
    fun notesCascadeFromPerson() {
        val store = TestStore()
        val person = Make.person(store.context)
        Make.note(store.context, title = "Consultation", person = person)
        store.context.save()

        store.context.delete(person)
        store.context.save()

        val notes = store.context.fetch<ConversationNote>()
        assertTrue(notes.isEmpty())
    }

    /** A note outlives the interaction it was logged with */
    @Test
    fun notesOutliveTheirInteraction() {
        val store = TestStore()
        val person = Make.person(store.context)
        val interaction = Make.interaction(store.context, kind = InteractionKind.MEETING, person = person)
        Make.note(
            store.context,
            title = "Consultation",
            person = person,
            sourceInteraction = interaction,
        )
        store.context.save()

        store.context.delete(interaction)
        store.context.save()

        assertEquals(1, person.noteList.size)
        assertNull(person.noteList.firstOrNull()?.sourceInteraction)
    }

    // MARK: - Search

    /** A phrase from inside a write-up finds the person */
    @Test
    fun noteBodiesAreSearchable() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake")
        Make.note(
            store.context,
            title = "Consultation · Mar 3",
            body = "He mentioned his brother runs a landscaping crew in Maple Grove.",
            person = person,
        )

        val reason = assertNotNull(PeopleEngine.reason(person, "landscaping"))
        assertEquals(SearchField.WRITE_UP, reason.field)
        assertEquals("Consultation · Mar 3", reason.snippet, "a line from mid-transcript reads as nonsense alone")
    }

    /** A note title is searchable too */
    @Test
    fun noteTitlesAreSearchable() {
        val store = TestStore()
        val person = Make.person(store.context, given = "Jake")
        Make.note(store.context, title = "Pricing conversation", person = person)

        assertTrue(PeopleEngine.matches(person, "pricing"))
    }
}
