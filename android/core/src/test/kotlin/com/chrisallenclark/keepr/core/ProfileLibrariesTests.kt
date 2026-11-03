package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.MemoryLibrary
import com.chrisallenclark.keepr.core.domain.NoteEditor
import com.chrisallenclark.keepr.core.domain.PersonFileLibrary
import com.chrisallenclark.keepr.core.domain.PersonFileImporter
import com.chrisallenclark.keepr.core.domain.PersonFilePayload
import com.chrisallenclark.keepr.core.model.ConversationNote
import com.chrisallenclark.keepr.core.model.Interaction
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.MemoryCategory
import com.chrisallenclark.keepr.core.model.NoteSource
import com.chrisallenclark.keepr.core.model.PersonFile
import com.chrisallenclark.keepr.core.model.PersonFileKind
import com.chrisallenclark.keepr.core.model.Priority
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The model work behind the notes composer, the files screen and the memory library. */
class ProfileLibrariesTests {

    private val calendar = TestDates.calendar
    private val now = TestDates.now

    // MARK: - Notes

    @Test
    fun savingANewNoteTrimsAndAttachesIt() {
        val store = TestStore()
        val person = Make.person(store.context)

        val note = NoteEditor.save(
            store.context, person, existing = null,
            title = "  Pricing call  ", body = "\n They want the annual plan. \n",
            occurredAt = TestDates.days(-2), source = NoteSource.PASTED, now = now, calendar = calendar,
        )

        assertEquals("Pricing call", note.title)
        assertEquals("They want the annual plan.", note.body)
        assertSame(person, note.person)
        assertEquals(listOf(note), person.noteList)
        assertTrue(store.fetch<Interaction>().isEmpty(), "nothing is logged unless asked")
    }

    @Test
    fun aBlankTitleFallsBackToTheDate() {
        val store = TestStore()
        val person = Make.person(store.context)
        val day = TestDates.date(2025, 3, 3)

        val note = NoteEditor.save(store.context, person, null, "  ", "", day, NoteSource.SCREENSHOT, now = now, calendar = calendar)

        assertEquals("Notes · Mar 3", note.title)
    }

    @Test
    fun screenshotsLiveInsideTheNoteNotLooseInFiles() {
        val store = TestStore()
        val person = Make.person(store.context)
        val image = PersonFilePayload(byteArrayOf(1, 2, 3), byteArrayOf(9), PersonFileKind.IMAGE, "jpg")

        val note = NoteEditor.save(store.context, person, null, "Results", "", now, NoteSource.SCREENSHOT, images = listOf(image), now = now, calendar = calendar)

        assertEquals(1, note.fileList.size)
        assertTrue(person.fileList.isEmpty(), "note images stay out of the Files list")
        assertEquals("1 image", note.lengthLabel)
    }

    @Test
    fun loggingANoteResetsTheirRhythmAndClearsWaiting() {
        val store = TestStore()
        val person = Make.person(store.context, lastInteractionAt = TestDates.days(-30))
        person.awaitingReplySince = TestDates.days(-5)
        val occurred = TestDates.days(-1)

        val note = NoteEditor.save(store.context, person, null, "Catch-up", "Long chat", occurred, NoteSource.TYPED, logAs = InteractionKind.MEETING, now = now, calendar = calendar)

        val interaction = store.fetch<Interaction>().single()
        assertEquals(InteractionKind.MEETING, interaction.kind)
        assertEquals("Catch-up", interaction.title)
        assertSame(interaction, note.sourceInteraction)
        assertEquals(occurred, person.lastInteractionAt)
        assertNull(person.awaitingReplySince)
    }

    @Test
    fun editingNeverLogsASecondInteraction() {
        val store = TestStore()
        val person = Make.person(store.context)
        val note = NoteEditor.save(store.context, person, null, "First", "Body", now, NoteSource.TYPED, now = now, calendar = calendar)

        val edited = NoteEditor.save(store.context, person, note, "Second", "New body", now, NoteSource.PASTED, logAs = InteractionKind.CALL, now = TestDates.days(1), calendar = calendar)

        assertSame(note, edited)
        assertEquals("Second", note.title)
        assertEquals("New body", note.body)
        assertEquals(NoteSource.PASTED, note.source)
        assertEquals(TestDates.days(1), note.updatedAt)
        assertTrue(store.fetch<Interaction>().isEmpty())
        assertEquals(1, store.fetch<ConversationNote>().size)
    }

    @Test
    fun saveNeedsTextOrAnImage() {
        assertFalse(NoteEditor.canSave("   \n", 0, null))
        assertTrue(NoteEditor.canSave("x", 0, null))
        assertTrue(NoteEditor.canSave("", 1, null))
    }

    @Test
    fun suggestedTitleUsesTheTextThenImages() {
        val day = TestDates.date(2025, 3, 3)
        assertEquals("", NoteEditor.suggestedTitle("  ", hasImages = false, date = day, calendar = calendar))
        assertEquals("Notes · Mar 3", NoteEditor.suggestedTitle("", hasImages = true, date = day, calendar = calendar))
        assertTrue(NoteEditor.suggestedTitle("Quarterly review with the team\nWe covered a lot.", false, day, calendar).isNotEmpty())
    }

    @Test
    fun pinningLeadsTheListAndFilterMatchesTitleOrBody() {
        val store = TestStore()
        val person = Make.person(store.context)
        val old = NoteEditor.save(store.context, person, null, "Old", "budget talk", TestDates.days(-10), NoteSource.TYPED, now = now, calendar = calendar)
        val recent = NoteEditor.save(store.context, person, null, "Recent", "nothing much", TestDates.days(-1), NoteSource.TYPED, now = now, calendar = calendar)
        assertEquals(listOf(recent, old), person.noteList)

        NoteEditor.togglePin(old, now)
        assertTrue(old.isPinned)
        assertEquals(listOf(old, recent), person.noteList)

        assertEquals(listOf(old), NoteEditor.filter(person.noteList, "BUDGET"))
        assertEquals(listOf(recent), NoteEditor.filter(person.noteList, "recent"))
        assertEquals(2, NoteEditor.filter(person.noteList, "  ").size)
    }

    @Test
    fun deletingANoteTakesItsScreenshotsButKeepsPulledFacts() {
        val store = TestStore()
        val person = Make.person(store.context)
        val image = PersonFilePayload(byteArrayOf(1), null, PersonFileKind.IMAGE, "jpg")
        val note = NoteEditor.save(store.context, person, null, "N", "Body", now, NoteSource.TYPED, images = listOf(image), now = now, calendar = calendar)
        Make.memory(store.context, "Likes tea", person = person)

        store.context.delete(note)

        assertTrue(store.fetch<PersonFile>().isEmpty())
        assertEquals(1, person.visibleMemories.size)
    }

    // MARK: - Files

    @Test
    fun fileNamesSplitIntoTitleAndExtension() {
        assertEquals("Signed form", PersonFileLibrary.baseName("Signed form.PDF"))
        assertEquals("pdf", PersonFileLibrary.extension("Signed form.PDF"))
        assertEquals("archive.tar", PersonFileLibrary.baseName("archive.tar.gz"))
        assertEquals("", PersonFileLibrary.extension("README"))
        assertEquals("README", PersonFileLibrary.baseName("README"))
        assertEquals(".profile", PersonFileLibrary.baseName(".profile"))
    }

    @Test
    fun documentsAreKeptByteForByte() {
        val bytes = "hello".toByteArray()
        val payload = PersonFileLibrary.document(bytes, "TXT", JvmImageCodec())

        assertContentEquals(bytes, payload.data)
        assertEquals(PersonFileKind.DOCUMENT, payload.kind)
        assertEquals("txt", payload.fileExtension)
        assertNull(payload.thumbnail)
    }

    @Test
    fun anImagePickedAsAFileGoesThroughThePhotoPath() {
        val payload = PersonFileLibrary.document(JvmImageCodec.jpeg(3000, 1000), "jpeg", JvmImageCodec())
        assertEquals(PersonFileKind.IMAGE, payload.kind)
        assertEquals("jpg", payload.fileExtension)
    }

    @Test
    fun insertedFilesGetADatedTitleUnlessTheyHaveAName() {
        val store = TestStore()
        val person = Make.person(store.context)
        val day = TestDates.date(2025, 3, 3, 10)
        val photo = PersonFilePayload(byteArrayOf(1), null, PersonFileKind.IMAGE, "jpg")

        val first = PersonFileLibrary.insert(store.context, person, photo, now = day, calendar = calendar)
        val second = PersonFileLibrary.insert(store.context, person, photo, now = day, calendar = calendar)
        val named = PersonFileLibrary.insert(store.context, person, photo, titledAfter = "Plan", now = day, calendar = calendar)

        assertEquals("Photo · Mar 3", first.title)
        assertEquals("Photo · Mar 3 (2)", second.title)
        assertEquals("Plan", named.title)
        assertEquals(3, person.fileList.size)
        assertEquals(day, person.updatedAt)
    }

    @Test
    fun detailsKeepANameWhenTheNewOneIsBlank() {
        val file = PersonFile("Scan", note = "old")
        PersonFileLibrary.updateDetails(file, "   ", TestDates.days(-3), "  ")
        assertEquals("Scan", file.title)
        assertEquals(TestDates.days(-3), file.capturedAt)
        assertNull(file.note)

        PersonFileLibrary.updateDetails(file, " Results ", now, " Fasted ")
        assertEquals("Results", file.title)
        assertEquals("Fasted", file.note)
    }

    @Test
    fun deletingAFileRemovesIt() {
        val store = TestStore()
        val person = Make.person(store.context)
        val file = PersonFileLibrary.insert(store.context, person, PersonFilePayload(byteArrayOf(1), null, PersonFileKind.PDF, "pdf"), now = now, calendar = calendar)

        PersonFileLibrary.delete(store.context, file, now)

        assertTrue(person.fileList.isEmpty())
        assertTrue(store.fetch<PersonFile>().isEmpty())
    }

    @Test
    fun countLabel() {
        assertEquals("1 file", PersonFileLibrary.countLabel(1))
        assertEquals("4 files", PersonFileLibrary.countLabel(4))
    }

    // MARK: - Bytes on demand

    @Test
    fun fileBytesCanBeReadOnDemandInsteadOfHeld() {
        val bytes = byteArrayOf(4, 5, 6)
        val file = PersonFile("Plan", data = bytes)
        assertSame(bytes, file.heldBytes)
        assertTrue(file.hasData)

        var reads = 0
        file.releaseData { reads++; bytes.copyOf() }
        assertNull(file.heldBytes, "released bytes aren't kept in memory")
        assertTrue(file.hasData)
        assertContentEquals(bytes, file.data)
        assertContentEquals(bytes, file.data)
        assertEquals(2, reads, "read each time, never cached")

        val replacement = byteArrayOf(7)
        file.data = replacement
        assertSame(replacement, file.data)
        assertNull(file.dataSource, "new bytes replace the old source")

        file.data = null
        assertFalse(file.hasData)
    }

    // MARK: - Memory library

    @Test
    fun libraryGroupsVisibleFactsAndSearches() {
        val store = TestStore()
        val person = Make.person(store.context)
        Make.memory(store.context, "Runs a roofing crew", category = MemoryCategory.WORK, person = person)
        Make.memory(store.context, "Daughter's wedding in spring", category = MemoryCategory.FAMILY, person = person)
        Make.memory(store.context, "Old fact", category = MemoryCategory.WORK, isArchived = true, person = person)

        val groups = MemoryLibrary.groups(person)
        assertEquals(listOf(MemoryCategory.WORK, MemoryCategory.FAMILY), groups.map { it.category })
        assertEquals(1, groups.first().count, "archived facts stay out")

        assertEquals(listOf(MemoryCategory.FAMILY), MemoryLibrary.groups(person, "WEDDING").map { it.category })
        assertEquals(listOf("Old fact"), MemoryLibrary.archived(person).map { it.content })
    }

    @Test
    fun archiveMoveStarAndEdit() {
        val store = TestStore()
        val person = Make.person(store.context)
        val memory = Make.memory(store.context, "Likes tea", person = person)

        MemoryLibrary.setArchived(memory, true, now)
        assertTrue(person.visibleMemories.isEmpty())
        MemoryLibrary.setArchived(memory, false, now)
        assertEquals(1, person.visibleMemories.size)

        MemoryLibrary.move(memory, MemoryCategory.PREFERENCES, now)
        assertEquals(MemoryCategory.PREFERENCES, memory.category)

        MemoryLibrary.toggleImportance(memory, now)
        assertEquals(Priority.HIGH, memory.importance)
        MemoryLibrary.toggleImportance(memory, now)
        assertEquals(Priority.NORMAL, memory.importance)

        assertFalse(MemoryLibrary.edit(memory, "   ", now))
        assertEquals("Likes tea", memory.content)
        assertTrue(MemoryLibrary.edit(memory, " Likes green tea ", now))
        assertEquals("Likes green tea", memory.content)
    }

    @Test
    fun refilingMovesFactsOutOfOther() {
        val store = TestStore()
        val person = Make.person(store.context)
        val other = Make.memory(store.context, "Her daughter just started college", person = person)

        val proposals = MemoryLibrary.refilable(person)
        val moved = MemoryLibrary.refile(proposals, now)

        assertEquals(proposals.size, moved)
        if (moved > 0) assertTrue(other.category != MemoryCategory.OTHER)
        assertEquals("One fact moved out of Other.", MemoryLibrary.refileMessage(1))
        assertEquals("3 facts moved out of Other.", MemoryLibrary.refileMessage(3))
    }

    @Test
    fun provenanceExplainsWhereAFactCameFrom() {
        val store = TestStore()
        val person = Make.person(store.context)
        val plain = Make.memory(store.context, "A", createdAt = TestDates.days(-3), person = person)
        val interaction = Make.interaction(store.context, kind = InteractionKind.CALL, title = "Budget Call", person = person)
        val sourced = Make.memory(store.context, "B", createdAt = TestDates.days(-3), person = person, sourceInteraction = interaction)

        assertTrue(MemoryLibrary.provenance(plain, now, calendar).startsWith("Added "))
        assertTrue(MemoryLibrary.provenance(sourced, now, calendar).startsWith("From budget call · "))
    }

    @Test
    fun importerTitleDefaultsUseKind() {
        assertEquals("PDF · Mar 3", PersonFileImporter.defaultTitle(PersonFileKind.PDF, TestDates.date(2025, 3, 3), calendar = calendar))
    }
}
