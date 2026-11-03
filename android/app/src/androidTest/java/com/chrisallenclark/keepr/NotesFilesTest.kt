package com.chrisallenclark.keepr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chrisallenclark.keepr.core.domain.PersonFileLibrary
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonFile
import com.chrisallenclark.keepr.core.model.PersonFileKind
import com.chrisallenclark.keepr.data.DiskFileBlobStore
import com.chrisallenclark.keepr.ui.files.PersonFileImport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Conversation notes, the memory library and profile files on a real
 * emulator: written through the UI where the UI can be driven, and through
 * the same import path the pickers use where it can't (system pickers and
 * the camera are other apps). Each checks its data again after a restart.
 */
@RunWith(AndroidJUnit4::class)
class NotesFilesTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun clean() {
        resetApp()
        File(context.filesDir, DiskFileBlobStore.DIRECTORY).deleteRecursively()
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun launch() = launchApp().also { scenario = it }

    private fun restart() {
        flushSaves()
        scenario?.close()
        AppContainer.resetForTesting()
        launch()
    }

    private fun addPerson(first: String, last: String) {
        rule.tapTab("People")
        rule.onNodeWithContentDescription("Add Person").performClick()
        rule.onNodeWithText("Add Manually").performClick()
        rule.waitForText("Add Person")
        rule.onNodeWithContentDescription("First name").performTextInput(first)
        rule.onNodeWithContentDescription("Last name").performTextInput(last)
        rule.onNodeWithText("Save").performClick()
        rule.waitForText("$first $last")
    }

    private fun openProfile(name: String) {
        rule.tapTab("People")
        rule.waitForText(name)
        rule.first(name).performClick()
        rule.waitForText("What You Know")
    }

    private fun longPress(text: String) {
        rule.onNodeWithText(text, substring = true).performScrollTo().performTouchInput { longClick() }
    }

    // MARK: - Notes

    @Test
    fun noteIsWrittenEditedAndDeletedAndSurvivesRestart() {
        launch()
        rule.waitForText("You're caught up.")
        addPerson("Nora", "Quinn")

        // Write one.
        rule.onNodeWithText("Paste a summary or meeting notes").performScrollTo().performClick()
        rule.waitForText("Add Note")
        rule.onNodeWithContentDescription("Paste the summary, or type what was said.")
            .performTextInput("They want the annual plan and a March start.")
        rule.onNodeWithContentDescription("Title").performScrollTo().performTextReplacement("Pricing call")
        rule.onNodeWithText("Save").performClick()
        rule.waitForText("Pricing call")
        rule.waitForText("They want the annual plan", timeoutMillis = 5_000)

        // It's read back from disk.
        restart()
        openProfile("Nora Quinn")
        rule.waitForText("Pricing call")

        // Edit it from the card's menu.
        longPress("Pricing call")
        rule.onNodeWithText("Edit").performClick()
        rule.waitForText("Edit Note")
        rule.onNodeWithContentDescription("Paste the summary, or type what was said.")
            .performTextReplacement("Annual plan, starting in April.")
        rule.onNodeWithText("Save").performClick()
        rule.waitForText("Annual plan, starting in April.")
        rule.waitForGone("They want the annual plan and a March start.")

        restart()
        openProfile("Nora Quinn")
        rule.waitForText("Annual plan, starting in April.")

        // Delete it, confirming first.
        longPress("Pricing call")
        rule.onNodeWithText("Delete").performClick()
        rule.waitForText("Delete this note?")
        rule.first("Delete").performClick()
        rule.waitForGone("Pricing call")
        rule.onNodeWithText("Paste a summary or meeting notes").performScrollTo()

        restart()
        openProfile("Nora Quinn")
        rule.onNodeWithText("Paste a summary or meeting notes").performScrollTo()
        assertTrue(rule.onAllNodesWithTextCount("Pricing call") == 0)
    }

    // MARK: - Memory library

    @Test
    fun memoryLibraryShowsEditsArchivesAndDeletes() {
        launch()
        rule.waitForText("You're caught up.")
        addPerson("Theo", "Banks")

        rule.onNodeWithText("Add one thing").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Something worth remembering").performTextInput("Keeps a sailboat at the marina")
        rule.onNodeWithText("Save").performClick()
        rule.waitForText("Keeps a sailboat at the marina")

        rule.onNodeWithText("See everything", substring = true).performScrollTo().performClick()
        rule.waitForText("Search what you know")
        rule.waitForText("Keeps a sailboat at the marina")

        // Edit
        longPress("Keeps a sailboat")
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithContentDescription("Something worth remembering").performTextReplacement("Keeps two sailboats at the marina")
        rule.onNodeWithText("Save").performClick()
        rule.waitForText("Keeps two sailboats at the marina")

        // Move to another shelf
        longPress("Keeps two sailboats")
        rule.onNodeWithText("Move to…").performClick()
        rule.waitForText("Health")
        rule.onNodeWithText("Health").performClick()
        rule.waitForText("Health")

        // Archive, then put it back
        longPress("Keeps two sailboats")
        rule.onNodeWithText("Archive").performClick()
        rule.waitForText("Archived")
        rule.waitForGone("Keeps two sailboats at the marina")
        rule.onNodeWithText("Show").performClick()
        longPress("Keeps two sailboats")
        rule.onNodeWithText("Restore").performClick()
        rule.waitForGone("Archived")
        rule.waitForText("Keeps two sailboats at the marina")

        // Survives a restart
        restart()
        openProfile("Theo Banks")
        rule.waitForText("Keeps two sailboats at the marina")
        rule.onNodeWithText("See everything", substring = true).performScrollTo().performClick()
        rule.waitForText("Search what you know")

        // Delete
        longPress("Keeps two sailboats")
        rule.onNodeWithText("Delete").performClick()
        rule.waitForText("Nothing recorded yet")
    }

    // MARK: - Files

    @Test
    fun importedPhotoAndPdfAreListedPreviewedAndKeptOnDisk() {
        launch()
        rule.waitForText("You're caught up.")
        addPerson("Iris", "Park")

        // What the photo picker and document picker would hand back.
        val photoFile = File(context.cacheDir, "test-photo.png").apply { writeBytes(pngBytes(3000, 2000)) }
        val pdfBytes = pdfBytes(pages = 2)
        val pdfFile = File(context.cacheDir, "Treatment plan.pdf").apply { writeBytes(pdfBytes) }

        val photo = runBlocking { PersonFileImport.photo(context, Uri.fromFile(photoFile)) }
        assertNotNull("a generated PNG decodes", photo)
        assertEquals(PersonFileKind.IMAGE, photo!!.kind)
        assertEquals("jpg", photo.fileExtension)
        assertNotNull(photo.thumbnail)

        val document = runBlocking { PersonFileImport.document(context, Uri.fromFile(pdfFile)) }
        assertNotNull(document)
        assertEquals(PersonFileKind.PDF, document!!.payload.kind)
        assertEquals("Treatment plan", document.title)
        assertArrayEquals("PDFs are kept byte for byte", pdfBytes, document.payload.data)
        assertNotNull("a PDF gets a first-page thumbnail", document.payload.thumbnail)

        val repository = AppContainer.get(context, isDemo = false).repository
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val person = repository.context.fetch<Person>().first { it.givenName == "Iris" }
            PersonFileLibrary.insert(repository.context, person, photo, null)
            PersonFileLibrary.insert(repository.context, person, document.payload, document.title)
            repository.context.save()
        }

        // The profile's card opens to the Files screen, which lists both.
        rule.waitForText("2 files")
        rule.onNodeWithText("2 files").performScrollTo().performClick()
        rule.onNodeWithText("Open Files").performScrollTo().performClick()
        rule.waitForText("Files are stored on this device only", timeoutMillis = 5_000)
        rule.waitForText("Treatment plan")
        rule.waitForText("Photo ·")

        // The PDF previews, page by page.
        rule.onNodeWithText("Treatment plan").performClick()
        waitForDescription("Page 1 of 2")
        rule.onNodeWithText("Done").performClick()

        // After a restart the bytes come back from app-private storage, not the database row.
        restart()
        rule.tapTab("People")
        rule.waitForText("Iris Park")
        var stored: PersonFile? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            stored = AppContainer.get(context, isDemo = false).repository.context.fetch<PersonFile>().first { it.title == "Treatment plan" }
        }
        val file = stored!!
        assertNull("bytes aren't held in memory after a launch", file.heldBytes)
        assertArrayEquals(pdfBytes, file.data)
        assertTrue(File(File(context.filesDir, DiskFileBlobStore.DIRECTORY), file.id.toString()).isFile)

        rule.first("Iris Park").performClick()
        rule.waitForText("2 files")
        rule.onNodeWithText("2 files").performScrollTo().performClick()
        rule.onNodeWithText("Open Files").performScrollTo().performClick()
        rule.waitForText("Treatment plan")
        rule.onNodeWithText("Treatment plan").performClick()
        waitForDescription("Page 1 of 2")
    }

    private fun waitForDescription(description: String) {
        try {
            rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription(description)).fetchSemanticsNodes().isNotEmpty() }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for [$description]. On screen:\n${rule.screenText()}", timeout)
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().size

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(40, 90, 160))
            drawCircle(width / 2f, height / 2f, height / 3f, Paint().apply { color = Color.WHITE })
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun pdfBytes(pages: Int): ByteArray {
        val document = PdfDocument()
        repeat(pages) { index ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, index + 1).create())
            page.canvas.drawText("Treatment plan, page ${index + 1}", 72f, 72f, Paint().apply { textSize = 24f })
            document.finishPage(page)
        }
        return ByteArrayOutputStream().also { document.writeTo(it); document.close() }.toByteArray()
    }
}
