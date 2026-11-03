package com.chrisallenclark.keepr.core

import com.chrisallenclark.keepr.core.domain.PersonFileImporter
import com.chrisallenclark.keepr.core.domain.PixelSize
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.PersonFile
import com.chrisallenclark.keepr.core.model.PersonFileKind
import java.io.File
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Translated from KeeprTests/PersonFileTests.swift. UIKit's image work is
// behind ImageCodec; these tests use the JDK-backed JvmImageCodec, so the
// photo tests still decode and encode real JPEGs.

/**
 * Files on a profile are the most private thing the app holds and the most
 * expensive thing it stores, so both halves are tested: the bytes have to come
 * down to a sane size, and nothing may go missing when a person is deleted.
 */
class PersonFileTests {

    private val codec = JvmImageCodec()

    // MARK: - Sizing

    @Test
    fun largePhotosAreScaledDown() {
        // A big photo is scaled to fit the budget on its long edge
        val fitted = PersonFileImporter.fittedSize(PixelSize(4032.0, 3024.0), maxPixelSize = 2200.0)

        assertEquals(2200.0, fitted.width)
        assertEquals(1650.0, fitted.height, "aspect ratio has to survive the resize")
    }

    @Test
    fun portraitUsesTheLongEdge() {
        // A portrait photo is measured on its height
        val fitted = PersonFileImporter.fittedSize(PixelSize(3024.0, 4032.0), maxPixelSize = 2200.0)

        assertEquals(2200.0, fitted.height)
        assertEquals(1650.0, fitted.width)
    }

    @Test
    fun smallPhotosAreNotUpscaled() {
        // A photo already inside the budget is left alone
        val original = PixelSize(800.0, 600.0)

        assertEquals(original, PersonFileImporter.fittedSize(original, maxPixelSize = 2200.0))
    }

    @Test
    fun degenerateSizeIsSafe() {
        // A zero-sized image doesn't divide by zero
        val fitted = PersonFileImporter.fittedSize(PixelSize.ZERO, maxPixelSize = 2200.0)

        assertEquals(PixelSize.ZERO, fitted)
    }

    // MARK: - Importing

    /** A real JPEG, drawn rather than fabricated, so the decode path is exercised. */
    private fun jpeg(width: Int, height: Int): ByteArray = JvmImageCodec.jpeg(width, height)

    @Test
    fun photoImportIsComplete() {
        // Importing a photo produces bytes, a thumbnail and a real extension
        val payload = assertNotNull(PersonFileImporter.photo(jpeg(3000, 2000), codec))

        assertEquals(PersonFileKind.IMAGE, payload.kind)
        assertEquals("jpg", payload.fileExtension)
        assertTrue(payload.byteCount > 0)
        assertTrue(payload.thumbnail != null, "lists must never fault in the original")
    }

    @Test
    fun photosAreCompressed() {
        // A consultation photo comes out far smaller than it went in
        val original = jpeg(4032, 3024)
        val payload = assertNotNull(PersonFileImporter.photo(original, codec))

        assertTrue(payload.byteCount < original.size)

        val decoded = assertNotNull(codec.decode(payload.data))
        assertTrue(max(decoded.size.width, decoded.size.height) <= PersonFileImporter.maxPixelSize)
    }

    @Test
    fun thumbnailIsSmall() {
        // The thumbnail is much smaller than the file it previews
        val payload = assertNotNull(PersonFileImporter.photo(jpeg(3000, 3000), codec))
        val thumbnail = assertNotNull(payload.thumbnail)

        assertTrue(thumbnail.size < payload.byteCount)

        val decoded = assertNotNull(codec.decode(thumbnail))
        assertTrue(max(decoded.size.width, decoded.size.height) <= PersonFileImporter.thumbnailPixelSize)
    }

    @Test
    fun undecodableDataIsRejected() {
        // Something that isn't an image fails instead of saving an empty row
        assertNull(PersonFileImporter.photo("not a photo".toByteArray(), codec))
    }

    @Test
    fun documentsAreStoredVerbatim() {
        // A document keeps its own bytes untouched
        val contents = "signed waiver".toByteArray()
        val payload = PersonFileImporter.file(contents, fileExtension = "txt", codec = codec)

        assertEquals(PersonFileKind.DOCUMENT, payload.kind)
        assertContentEquals(contents, payload.data, "a signed form has to stay the file it was")
        assertEquals("txt", payload.fileExtension)
    }

    @Test
    fun imagesFromFilesAreCompressed() {
        // An image picked out of Files still goes down the photo path
        val original = jpeg(4000, 3000)
        val payload = PersonFileImporter.file(original, fileExtension = "JPEG", codec = codec)

        assertEquals(PersonFileKind.IMAGE, payload.kind)
        assertTrue(payload.byteCount < original.size)
    }

    @Test
    fun missingExtensionIsHandled() {
        // A file with no extension still saves
        val payload = PersonFileImporter.file(byteArrayOf(0x00, 0x01), fileExtension = "", codec = codec)

        assertEquals(PersonFileKind.OTHER, payload.kind)
        assertEquals("dat", payload.fileExtension)
    }

    @Test
    fun extensionParsingIsForgiving() {
        // Extensions are read case-insensitively, dot or no dot
        assertEquals(PersonFileKind.PDF, PersonFileKind.forExtension("PDF"))
        assertEquals(PersonFileKind.IMAGE, PersonFileKind.forExtension("heic"))
        assertEquals("txt", PersonFileImporter.file(byteArrayOf(0x25), fileExtension = ".TXT", codec = codec).fileExtension)
    }

    // MARK: - Naming

    @Test
    fun defaultTitleIsReadable() {
        // A new file is named after what it is and when, not IMG_4821
        val title = PersonFileImporter.defaultTitle(PersonFileKind.IMAGE, date = TestDates.now)

        assertTrue(title.startsWith("Photo · "))
        assertFalse(title.isEmpty())
    }

    @Test
    fun duplicateTitlesAreNumbered() {
        // Two files added the same day get told apart
        val first = PersonFileImporter.defaultTitle(PersonFileKind.IMAGE, date = TestDates.now)
        val second = PersonFileImporter.defaultTitle(
            PersonFileKind.IMAGE,
            date = TestDates.now,
            existingTitles = listOf(first),
        )
        val third = PersonFileImporter.defaultTitle(
            PersonFileKind.IMAGE,
            date = TestDates.now,
            existingTitles = listOf(first, second),
        )

        assertEquals("$first (2)", second)
        assertEquals("$first (3)", third)
    }

    @Test
    fun sharedFileNameIsSafe() {
        // A shared file gets a clean filename
        val store = TestStore()
        val file = Make.file(
            store.context,
            title = "InBody / Week 1: 24% BF",
            fileExtension = "pdf",
        )

        val name = file.suggestedFileName
        assertTrue(name.endsWith(".pdf"))
        assertFalse(name.contains("/"), "a slash in a filename is a path, not a name")
        assertFalse(name.contains(":"))
    }

    @Test
    fun unnameableFileFallsBack() {
        // A file with an unusable name still gets one
        val store = TestStore()
        val file = Make.file(store.context, title = "///", fileExtension = "jpg")

        assertEquals("File.jpg", file.suggestedFileName)
    }

    // MARK: - On the record

    @Test
    fun filesAreNewestFirst() {
        // Files come back newest first
        val store = TestStore()
        val person = Make.person(store.context)
        Make.file(store.context, title = "Week 1", capturedAt = TestDates.days(-60), person = person)
        Make.file(store.context, title = "Week 12", capturedAt = TestDates.days(-1), person = person)
        Make.file(store.context, title = "Week 6", capturedAt = TestDates.days(-30), person = person)

        assertEquals(listOf("Week 12", "Week 6", "Week 1"), person.fileList.map { it.title })
    }

    @Test
    fun filesCascadeOnDelete() {
        // Deleting someone takes their files with them
        val store = TestStore()
        val person = Make.person(store.context)
        Make.file(store.context, title = "Results", person = person)
        store.context.save()

        store.context.delete(person)
        store.context.save()

        val remaining = store.context.fetch<PersonFile>()
        assertTrue(remaining.isEmpty(), "a deleted person must not leave their scans behind")
    }

    @Test
    fun filesOutliveTheirInteraction() {
        // A file survives the interaction it came from being deleted
        val store = TestStore()
        val person = Make.person(store.context)
        val consultation = Make.interaction(store.context, kind = InteractionKind.MEETING, person = person)
        val file = Make.file(store.context, title = "Results", person = person)
        file.sourceInteraction = consultation
        store.context.save()

        store.context.delete(consultation)
        store.context.save()

        assertEquals(1, person.fileList.size)
        assertNull(person.fileList.firstOrNull()?.sourceInteraction)
    }

    @Test
    fun sizeIsHumanReadable() {
        // Size is reported in units a person reads
        val store = TestStore()
        val file = Make.file(store.context, title = "Scan", byteCount = 418_000)

        assertEquals(false, file.sizeLabel.isEmpty())
        assertTrue(file.sizeLabel.contains("418") || file.sizeLabel.contains("KB"))
    }

    @Test
    fun symbolsExist() {
        // Every file-kind symbol is a real SF Symbol — on Android, one the app's
        // symbol table maps to a shipped drawable (an unknown name falls back to
        // a placeholder circle). Read from source: core can't see the app's R.
        val table = File("../app/src/main/java/com/chrisallenclark/keepr/ui/icons/SfSymbolTable.kt")
        val drawables = File("../app/src/main/res/drawable")
        assertTrue(table.isFile, "symbol table not found at ${table.absolutePath}")
        val source = table.readText()
        for (kind in PersonFileKind.entries) {
            val resource = "sf_" + kind.symbolName.replace('.', '_')
            assertTrue(
                source.contains("\"${kind.symbolName}\" to R.drawable.$resource,") &&
                    File(drawables, "$resource.xml").isFile,
                "${kind.symbolName} does not exist",
            )
        }
    }
}
