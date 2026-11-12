package com.aulorvy.keepr

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aulorvy.keepr.core.model.ConversationNote
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.RelationshipContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The whole backup round trip the way a person does it, through Settings:
 * Back Up to File… → password → (file picker) → saved; then Restore from
 * Backup… → (file picker) → password → preview → Replace → restored.
 *
 * The system file picker can't be driven by a test, so Espresso-Intents
 * answers it with a file in the app's cache, exactly as the picker would
 * answer with the document the person chose.
 */
@RunWith(AndroidJUnit4::class)
class BackupSettingsFlowTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val backupFile = File(app.cacheDir, "settings-flow-test.keeprbackup")
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        resetApp()
        backupFile.delete()
        Intents.init()
        val picked = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(backupFile)))
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(picked)
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(picked)
    }

    @After
    fun tearDown() {
        Intents.release()
        scenario?.close()
        backupFile.delete()
    }

    private fun container() = AppContainer.get(InstrumentationRegistry.getInstrumentation().targetContext, isDemo = false)

    private fun people(): List<String> {
        var names: List<String> = emptyList()
        rule.runOnIdle { names = container().repository.context.fetch<Person>().filter { !it.isDeleted }.map { it.fullName }.sorted() }
        return names
    }

    private fun openSettings() {
        rule.waitForText("Good ")
        rule.waitForIdle()
        repeat(3) { attempt ->
            rule.onNodeWithContentDescription("Settings").tap()
            try {
                rule.waitForText("Follow-up reminders", timeoutMillis = 5_000)
                return
            } catch (missed: AssertionError) {
                if (attempt == 2) throw missed
            }
        }
    }

    @Test
    fun backUpThroughSettingsThenRestoreReplacesWhatChanged() {
        // Someone worth keeping, with a note.
        runBlocking {
            withContext(Dispatchers.Main) {
                container().load()
                val ctx = container().repository.context
                val rosa = Person("Rosa", "Lindqvist", context = RelationshipContext.BUSINESS, company = "Northwind Clinic")
                ctx.insert(rosa)
                ctx.insert(ConversationNote(body = "Wants the clinic rollout in March.", title = "Rollout call", person = rosa))
                ctx.save()
            }
            container().repository.flush()
        }
        scenario = launchApp()
        openSettings()

        // Back up.
        rule.onNodeWithText("Back Up to File…").performScrollTo().tap()
        rule.waitForText("Back Up to a File")
        rule.onNodeWithContentDescription("Password").performTextInput("correct horse battery")
        rule.onNodeWithContentDescription("Confirm Password").performTextInput("correct horse battery")
        rule.onNodeWithText("Next").tap()
        rule.waitForText("Backup Saved", timeoutMillis = 60_000)
        assertTrue("the backup file was written", backupFile.length() > 0)
        assertEquals("KEEPRBK1", String(backupFile.readBytes().copyOf(8), Charsets.US_ASCII))
        assertTrue("no plaintext in the file", !String(backupFile.readBytes(), Charsets.ISO_8859_1).contains("Lindqvist"))
        // "Done" is also Settings' own button underneath; the backup sheet's is the last one.
        rule.onAllNodesWithText("Done").onLast().tap()
        rule.waitForGone("Backup Saved")

        // Things change after the backup: someone new is added.
        runBlocking {
            withContext(Dispatchers.Main) {
                container().repository.context.insert(Person("Temp", "Visitor", context = RelationshipContext.BUSINESS))
                container().repository.context.save()
            }
            container().repository.flush()
        }
        assertEquals(listOf("Rosa Lindqvist", "Temp Visitor"), people())

        // Restore: the phone has data, so it asks before replacing it.
        rule.waitForText("Follow-up reminders")
        rule.onNodeWithText("Restore from Backup…").performScrollTo().tap()
        rule.waitForText("Restore from Backup")
        rule.onNodeWithContentDescription("Backup Password").performTextInput("correct horse battery")
        rule.onNodeWithText("Open").tap()
        rule.waitForText("Everything in it checks out.", timeoutMillis = 60_000)
        rule.onNodeWithText("Replace with Backup").performScrollTo().tap()
        rule.waitForText("Replace 2 people with 1 person from the backup?")
        assertEquals("nothing changes while the question is up", listOf("Rosa Lindqvist", "Temp Visitor"), people())
        rule.first("Replace").tap()
        rule.waitUntil(60_000) { people() == listOf("Rosa Lindqvist") }

        // What was backed up is back, and the later addition is gone.
        var noteBody: String? = null
        rule.runOnIdle {
            noteBody = container().repository.context.fetch<ConversationNote>().singleOrNull { !it.isDeleted }?.body
        }
        assertEquals("Wants the clinic rollout in March.", noteBody)
    }
}
