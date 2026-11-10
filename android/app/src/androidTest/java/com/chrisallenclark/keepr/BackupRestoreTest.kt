package com.chrisallenclark.keepr

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chrisallenclark.keepr.backup.BackupService
import com.chrisallenclark.keepr.core.backup.BackupException
import com.chrisallenclark.keepr.core.backup.BackupSettings
import com.chrisallenclark.keepr.core.model.ConversationNote
import com.chrisallenclark.keepr.core.model.ContextMode
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Interaction
import com.chrisallenclark.keepr.core.model.InteractionKind
import com.chrisallenclark.keepr.core.model.Memory
import com.chrisallenclark.keepr.core.model.MemoryCategory
import com.chrisallenclark.keepr.core.model.NoteSource
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonFile
import com.chrisallenclark.keepr.core.model.PersonFileKind
import com.chrisallenclark.keepr.core.model.PersonGroup
import com.chrisallenclark.keepr.core.model.PersonLink
import com.chrisallenclark.keepr.core.model.Priority
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.model.RelationshipTag
import com.chrisallenclark.keepr.core.model.TagKind
import com.chrisallenclark.keepr.core.persistence.KeeprStore
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.core.services.FollowUpScheduler
import com.chrisallenclark.keepr.data.DiskFileBlobStore
import com.chrisallenclark.keepr.data.KeeprDatabase
import com.chrisallenclark.keepr.data.Rows
import com.chrisallenclark.keepr.data.toRows
import com.chrisallenclark.keepr.notifications.AndroidNotificationService
import com.chrisallenclark.keepr.notifications.rescheduleAllReminders
import com.chrisallenclark.keepr.ui.backup.RestoreSheet
import com.chrisallenclark.keepr.ui.screens.AppState
import com.chrisallenclark.keepr.ui.screens.LocalAppState
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * Backing up to a file and restoring from it, through BackupService — the
 * same code the Settings and onboarding screens run. Only the system file
 * picker is skipped: the tests hand the service a stream to a file in the
 * app's cache instead.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val password = "tulip-harbor-42".toCharArray()
    private val backupFile = File(context.cacheDir, "test-backup.keeprbackup")
    private val blobDirectory = File(context.filesDir, DiskFileBlobStore.DIRECTORY)
    private val fileBytes = Random(42).nextBytes(700_000) // several encryption chunks
    private val thumbnailBytes = Random(7).nextBytes(2_000)
    private val photoBytes = Random(3).nextBytes(1_500)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        freshPhone()
        backupFile.delete()
    }

    @After
    fun tearDown() {
        BackupService.afterRowsWrittenForTesting = null
        backupFile.delete()
        AndroidNotificationService(context).cancelAllNow()
    }

    // MARK: - Helpers

    /** A phone that has never run Keepr: no database, no files, no settings, no reminders. */
    private fun freshPhone() {
        resetApp()
        blobDirectory.deleteRecursively()
        File(context.filesDir, DiskFileBlobStore.DIRECTORY + ".previous").deleteRecursively()
    }

    private fun container(): AppContainer = AppContainer.get(context, isDemo = false)

    private fun <T> onMain(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }

    /** Loads the store (as a launch does) and returns the state the screens would get. */
    private fun launchStore(): AppState {
        val c = container()
        onMain { c.load() }
        return AppState(c.repository, c.notificationService, c.repository.scope, contacts = c.contactStore)
    }

    /** As if the app were killed and opened again: everything read back from disk. */
    private fun relaunch(): AppState {
        flushSaves()
        AppContainer.resetForTesting()
        return launchStore()
    }

    private fun pending(): List<String> = AndroidNotificationService(context).pendingIdentifiers()

    private fun waitForPending(identifier: String, timeoutMillis: Long = 10_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (identifier !in pending()) {
            if (SystemClock.uptimeMillis() > deadline) fail("Reminder $identifier never scheduled. Pending: ${pending()}")
            Thread.sleep(100)
        }
    }

    private class Fixture(val alice: Person, val bob: Person, val followUp: FollowUp, val file: PersonFile, val deletedId: String)

    /** One of everything, joined up the way the app joins it. */
    private fun createRichData(state: AppState): Fixture = onMain {
        val ctx = state.context
        val clientTag = RelationshipTag("Climbing partner", TagKind.PERSONAL, symbolName = "figure.climbing").also(ctx::insert)
        val builtIn = ctx.fetch<RelationshipTag>().first { it.isBuiltIn }
        val gym = PersonGroup("Life Time", symbolName = "figure.run", detail = "The Edina club", context = RelationshipContext.PERSONAL).also(ctx::insert)
        val alice = Person(
            "Alice", "Ng", context = RelationshipContext.BOTH, company = "Acme", jobTitle = "CTO",
            phoneNumbers = listOf("+1 555 0100", "+1 555 0101"), emailAddresses = listOf("alice@example.com"),
            photoData = photoBytes, priority = Priority.HIGH, isFavorite = true, howWeMet = "Climbing gym",
            notes = "Allergic to peanuts. Loves ☕️",
        ).also(ctx::insert)
        alice.cadenceDays = 21
        alice.birthday = Instant.parse("1990-04-12T00:00:00Z")
        val bob = Person("Bob", "Rivera", context = RelationshipContext.BUSINESS).also(ctx::insert)
        alice.tags = listOf(clientTag, builtIn)
        alice.groups = listOf(gym)
        bob.tags = listOf(builtIn)
        val lunch = Interaction(InteractionKind.MEAL, Instant.now().minus(Duration.ofDays(3)), title = "Lunch at Hai Hai", rawNote = "Talked about the move", person = alice)
            .also(ctx::insert)
        ctx.insert(Memory("Daughter starts college in the fall", MemoryCategory.FAMILY, Priority.HIGH, person = alice, sourceInteraction = lunch))
        val note = ConversationNote("Move plan", "Boxes on Friday\nTruck on Saturday", NoteSource.TYPED, isPinned = true, person = alice, sourceInteraction = lunch)
            .also(ctx::insert)
        val followUp = FollowUp(
            "Send the moving checklist", note = "The one from last year", dueDate = Instant.now().plus(Duration.ofDays(2)),
            hasTime = true, priority = Priority.HIGH, person = alice, sourceInteraction = lunch,
        ).also(ctx::insert)
        val file = PersonFile(
            "Lease", PersonFileKind.PDF, "pdf", data = fileBytes, thumbnailData = thumbnailBytes, byteCount = fileBytes.size,
            note = "Page 2", person = alice, sourceInteraction = lunch, conversationNote = note,
        ).also(ctx::insert)
        ctx.insert(PersonLink(alice, bob, "Manager of", "Reports to", note = "Since 2024"))
        // A deletion, so there's a tombstone to carry.
        val temp = Person("Temporary", "Person").also(ctx::insert)
        state.save()
        val deletedId = temp.id.toString()
        ctx.delete(temp)
        state.save()
        state.repository.preferences.setString(PreferenceKey.CONTEXT_MODE, ContextMode.PERSONAL.rawValue)
        state.repository.preferences.setString(PreferenceKey.GROUP_LABEL, "Place")
        state.rescheduleAllReminders()
        // Scheduling stamps the follow-up's reminder id; keep it on disk too.
        state.save()
        Fixture(alice, bob, followUp, file, deletedId)
    }.also { flushSaves() }

    /** Every row, with byte columns set aside (arrays compare by identity) and in a fixed order. */
    private fun comparable(rows: Rows): Rows = rows.copy(
        people = rows.people.map { it.copy(photoData = null) }.sortedBy { it.id },
        interactions = rows.interactions.sortedBy { it.id },
        memories = rows.memories.sortedBy { it.id },
        followUps = rows.followUps.sortedBy { it.id },
        notes = rows.notes.sortedBy { it.id },
        files = rows.files.map { it.copy(data = null, thumbnailData = null) }.sortedBy { it.id },
        tags = rows.tags.sortedBy { it.id },
        groups = rows.groups.sortedBy { it.id },
        links = rows.links.sortedBy { it.id },
        personTags = rows.personTags.sortedWith(compareBy({ it.personId }, { it.tagId })),
        groupMembers = rows.groupMembers.sortedWith(compareBy({ it.personId }, { it.groupId })),
    )

    private fun snapshot(state: AppState): Rows = onMain { comparable(state.context.toRows()) }

    private fun backUp(state: AppState) {
        backupFile.outputStream().use { out -> onMain { BackupService(state, context.cacheDir).backUp(out, password) } }
        assertTrue(backupFile.length() > fileBytes.size)
    }

    private fun stageAndRestore(state: AppState, with: CharArray = password) {
        val service = BackupService(state, context.cacheDir)
        val staged = runBlocking { backupFile.inputStream().use { service.stage(it, with) } }
        onMain { service.restore(staged) }
    }

    private fun fileData(state: AppState, id: String): ByteArray? {
        val file = onMain { state.context.fetch<PersonFile>().firstOrNull { it.id.toString() == id } } ?: return null
        return file.data
    }

    private fun stagingIsGone() = assertFalse(File(context.cacheDir, BackupService.STAGING_DIRECTORY).exists())

    // MARK: - Tests

    @Test
    fun backupRestoresEverythingOnANewPhone() {
        var state = launchStore()
        val fixture = createRichData(state)
        val reminder = onMain { FollowUpScheduler.identifier(fixture.followUp) }
        waitForPending(reminder)
        val before = snapshot(state)
        backUp(state)

        // The old phone is gone. A new one runs Keepr for the first time.
        freshPhone()
        state = launchStore()
        assertEquals(0, onMain { state.context.fetch<Person>().size })
        assertFalse(reminder in pending())

        stageAndRestore(state)
        stagingIsGone()

        // In memory straight away…
        assertEquals(before, snapshot(state))
        // …and on disk, as a relaunch reads it.
        state = relaunch()
        assertEquals(before, snapshot(state))

        onMain {
            val alice = state.context.fetch<Person>().single { it.givenName == "Alice" }
            assertArrayEquals(photoBytes, alice.photoData)
            assertEquals(listOf("Climbing partner"), alice.tags.filter { !it.isBuiltIn }.map { it.name })
            assertEquals(listOf("Life Time"), alice.groups.map { it.name })
            assertEquals("Bob", alice.connections.single().person.givenName)
            assertEquals(1, alice.memories.size)
            assertEquals("Lunch at Hai Hai", alice.memories.single().sourceInteraction?.title)
            assertEquals("Move plan", alice.files.single().conversationNote?.title)
            assertArrayEquals(thumbnailBytes, alice.files.single().thumbnailData)
        }
        assertArrayEquals(fileBytes, fileData(state, fixture.file.id.toString()))
        assertTrue(File(blobDirectory, fixture.file.id.toString()).isFile)

        // Settings that matter came along; the contact baseline didn't.
        val prefs = state.repository.preferences
        assertEquals(ContextMode.PERSONAL.rawValue, prefs.string(PreferenceKey.CONTEXT_MODE))
        assertEquals("Place", prefs.string(PreferenceKey.GROUP_LABEL))
        assertTrue(prefs.bool(PreferenceKey.HAS_ONBOARDED))
        assertFalse(state.contactTracker.hasBaseline)
        assertTrue(BackupSettings.included.containsKey(PreferenceKey.REMINDERS_ENABLED))

        // The follow-up's reminder is set again from the restored data.
        waitForPending(reminder)

        // The deletion tombstone travelled too.
        flushSaves()
        AppContainer.resetForTesting()
        val database = KeeprDatabase.open(context)
        try {
            val tombstones = runBlocking { database.dao().deletions() }
            assertTrue(tombstones.any { it.id == fixture.deletedId && it.recordType == "people" })
        } finally {
            database.close()
        }
    }

    @Test
    fun restoreAfterDeleteAllDataBringsItBack() {
        var state = launchStore()
        createRichData(state)
        val before = snapshot(state)
        backUp(state)

        // Settings → Delete All Data, as the button does it.
        onMain {
            state.notifications.cancelAll()
            KeeprStore.deleteAllData(state.context, state.repository.preferences)
            state.contactTracker.reset()
        }
        flushSaves()
        assertEquals(0, onMain { state.context.fetch<Person>().size })

        stageAndRestore(state)
        assertEquals(before, snapshot(state))
        state = relaunch()
        assertEquals(before, snapshot(state))
    }

    @Test
    fun wrongPasswordChangesNothing() {
        var state = launchStore()
        val fixture = createRichData(state)
        backUp(state)
        val before = snapshot(state)

        val service = BackupService(state, context.cacheDir)
        try {
            runBlocking { backupFile.inputStream().use { service.stage(it, "not-the-password".toCharArray()) } }
            fail("A wrong password was accepted")
        } catch (expected: BackupException.WrongPassword) {
            assertEquals("That password doesn't open this backup. Check it and try again.", expected.message)
        }
        stagingIsGone()
        assertEquals(before, snapshot(state))
        state = relaunch()
        assertEquals(before, snapshot(state))
        assertArrayEquals(fileBytes, fileData(state, fixture.file.id.toString()))
    }

    @Test
    fun damagedFileChangesNothing() {
        var state = launchStore()
        createRichData(state)
        backUp(state)
        val before = snapshot(state)
        // Flip one byte well inside the encrypted data.
        val bytes = backupFile.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        backupFile.writeBytes(bytes)

        try {
            stageAndRestore(state)
            fail("A damaged backup was restored")
        } catch (expected: BackupException.Damaged) {
            // The whole file is checked before anything is replaced.
        }
        stagingIsGone()
        assertEquals(before, snapshot(state))
        state = relaunch()
        assertEquals(before, snapshot(state))
    }

    @Test
    fun failureDuringTheTransactionLeavesEverythingAsItWas() {
        var state = launchStore()
        val fixture = createRichData(state)
        backUp(state)

        // Change things after the backup, including a file only this version has.
        val newBytes = Random(99).nextBytes(50_000)
        val newFileId = onMain {
            val carol = Person("Carol", "Diaz").also(state.context::insert)
            val receipt = PersonFile("Receipt", PersonFileKind.IMAGE, "jpg", data = newBytes, byteCount = newBytes.size, person = carol)
                .also(state.context::insert)
            state.context.delete(state.context.fetch<Person>().single { it.givenName == "Bob" })
            state.save()
            receipt.id.toString()
        }
        flushSaves()
        val before = snapshot(state)

        BackupService.afterRowsWrittenForTesting = { throw IllegalStateException("Injected failure") }
        try {
            stageAndRestore(state)
            fail("The injected failure didn't stop the restore")
        } catch (expected: IllegalStateException) {
            assertEquals("Injected failure", expected.message)
        }
        BackupService.afterRowsWrittenForTesting = null
        stagingIsGone()
        assertFalse(File(context.filesDir, DiskFileBlobStore.DIRECTORY + ".previous").exists())

        // In memory, on disk, and every file's bytes: untouched.
        assertEquals(before, snapshot(state))
        assertArrayEquals(newBytes, fileData(state, newFileId))
        assertArrayEquals(fileBytes, fileData(state, fixture.file.id.toString()))

        // Saves work again afterwards.
        onMain {
            state.context.insert(Person("Dana", "Lee"))
            state.save()
        }
        state = relaunch()
        val names = onMain { state.context.fetch<Person>().map { it.givenName }.sorted() }
        assertEquals(listOf("Alice", "Carol", "Dana"), names)
        assertArrayEquals(newBytes, fileData(state, newFileId))
        assertArrayEquals(fileBytes, fileData(state, fixture.file.id.toString()))
    }

    @Test
    fun restoringOverExistingDataAsksFirst() {
        val state = launchStore()
        createRichData(state)
        backUp(state)
        onMain {
            state.context.insert(Person("Carol", "Diaz"))
            state.save()
        }
        flushSaves()

        var restoredMode: ContextMode? = null
        rule.setContent {
            KeeprTheme {
                CompositionLocalProvider(LocalAppState provides state) {
                    RestoreSheet(Uri.fromFile(backupFile), onDismiss = {}, onRestored = { restoredMode = it.mode })
                }
            }
        }
        rule.waitForText("Backup Password")
        rule.onNodeWithContentDescription("Backup Password").performTextInput(String(password))
        rule.onNodeWithText("Open").tap()
        rule.waitForText("In This Backup", timeoutMillis = 60_000)
        rule.waitForText("On This Phone Now")

        // The phone has data, so the button asks first, naming what's replaced by what…
        rule.onNodeWithText("Replace with Backup").performScrollTo().tap()
        rule.waitForText("Replace 3 people with 2 people from the backup?")
        rule.waitForIdle()
        // …and while it's asking, nothing has changed.
        assertNotNull(onMain { state.context.fetch<Person>().firstOrNull { it.givenName == "Carol" } })

        // Saying yes replaces everything.
        rule.onNodeWithText("Replace").tap()
        rule.waitForText("Restore Complete", timeoutMillis = 60_000)
        val names = onMain { state.context.fetch<Person>().map { it.givenName }.sorted() }
        assertEquals(listOf("Alice", "Bob"), names)
        assertEquals(ContextMode.PERSONAL, restoredMode)
        stagingIsGone()
    }

    @Test
    fun wrongPasswordInTheSheetSaysSo() {
        val state = launchStore()
        createRichData(state)
        backUp(state)

        rule.setContent {
            KeeprTheme {
                CompositionLocalProvider(LocalAppState provides state) {
                    RestoreSheet(Uri.fromFile(backupFile), onDismiss = {}, onRestored = {})
                }
            }
        }
        rule.waitForText("Backup Password")
        rule.onNodeWithContentDescription("Backup Password").performTextInput("guess-guess-guess")
        rule.onNodeWithText("Open").tap()
        rule.waitForText("That password doesn't open this backup", timeoutMillis = 60_000)
        assertEquals(2, onMain { state.context.fetch<Person>().size })
    }
}
