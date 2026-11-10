package com.chrisallenclark.keepr

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chrisallenclark.keepr.core.model.ConversationNote
import com.chrisallenclark.keepr.core.model.FollowUp
import com.chrisallenclark.keepr.core.model.Person
import com.chrisallenclark.keepr.core.model.PersonFile
import com.chrisallenclark.keepr.core.model.PersonFileKind
import com.chrisallenclark.keepr.core.model.RelationshipContext
import com.chrisallenclark.keepr.core.services.FollowUpScheduler
import com.chrisallenclark.keepr.notifications.AndroidNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Proof that a tester's data survives installing a newer release over an
 * older one. CI runs it against the minified, non-debuggable release build
 * (scripts/release-upgrade-check.sh): the write phase on version N, then
 * `adb install -r` of version N+1, then the read phase. In an ordinary test
 * run both are skipped.
 *
 * No Compose test rule on purpose: this runs against R8-processed code, so it
 * talks to the app's own store and reminder list, and the shell script checks
 * the UI by launching the app the normal way.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseUpgradeTest {

    private val arguments = InstrumentationRegistry.getArguments()
    private val phase: String? = arguments.getString("keeprPhase")

    @Test
    fun writeBeforeUpdate() {
        assumeTrue(phase == "write")
        resetApp()
        val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = AppContainer.get(appContext, isDemo = false)
        val reminderAt = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.MINUTES)
        var followUp: FollowUp? = null
        runBlocking {
            withContext(Dispatchers.Main) {
                container.load()
                val store = container.repository.context
                val person = Person(
                    GIVEN_NAME,
                    FAMILY_NAME,
                    context = RelationshipContext.BUSINESS,
                    contactIdentifier = CONTACT_IDENTIFIER,
                    company = COMPANY,
                )
                store.insert(person)
                store.insert(ConversationNote(NOTE_TITLE, NOTE_BODY, person = person))
                val bytes = fileBytes()
                store.insert(
                    PersonFile(
                        FILE_TITLE,
                        kind = PersonFileKind.OTHER,
                        fileExtension = "bin",
                        data = bytes,
                        byteCount = bytes.size,
                        person = person,
                    ),
                )
                val task = FollowUp(FOLLOW_UP_TITLE, dueDate = reminderAt, hasTime = true, person = person)
                task.notificationIdentifier = FollowUpScheduler.identifier(task)
                store.insert(task)
                store.save()
                followUp = task
            }
            container.repository.flush()
        }

        // Scheduled the way the app does it, straight into the pending list
        // and AlarmManager. The script grants POST_NOTIFICATIONS first on 13+.
        val reminder = FollowUpScheduler.reminder(followUp!!)
        assertNotNull("the follow-up has a reminder", reminder)
        val reminders = AndroidNotificationService(appContext)
        reminders.scheduleNow(reminder!!.toLocalReminder())
        assertNotNull(
            "the reminder is pending (are notifications allowed for the app?)",
            reminders.pendingReminder(reminder.identifier),
        )
    }

    @Test
    fun readAfterUpdate() {
        assumeTrue(phase == "read")
        val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

        // The app under test is the update, and a release build.
        arguments.getString("keeprVersionCode")?.let { expected ->
            assertEquals("installed versionCode", expected.toLong(), installedVersionCode(appContext))
        }
        assertEquals(
            "the installed app must not be debuggable",
            0,
            appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE,
        )

        val container = AppContainer.get(appContext, isDemo = false)
        var people: List<Person> = emptyList()
        var notes: List<ConversationNote> = emptyList()
        var files: List<PersonFile> = emptyList()
        var followUps: List<FollowUp> = emptyList()
        runBlocking {
            withContext(Dispatchers.Main) {
                container.load()
                val store = container.repository.context
                people = store.fetch<Person>().filter { it.givenName == GIVEN_NAME }
                notes = store.fetch<ConversationNote>().filter { it.title == NOTE_TITLE }
                files = store.fetch<PersonFile>().filter { it.title == FILE_TITLE }
                followUps = store.fetch<FollowUp>().filter { it.title == FOLLOW_UP_TITLE }
            }
        }

        assertEquals("the person survived the update", 1, people.size)
        val person = people.single()
        assertEquals(FAMILY_NAME, person.familyName)
        assertEquals(COMPANY, person.company)
        assertEquals(RelationshipContext.BUSINESS, person.context)
        assertEquals("the imported-contact link survived", CONTACT_IDENTIFIER, person.contactIdentifier)

        assertEquals("the note survived the update", 1, notes.size)
        assertEquals(NOTE_BODY, notes.single().body)
        assertTrue("the note is still on the person", notes.single().person === person)

        assertEquals("the file survived the update", 1, files.size)
        val file = files.single()
        assertTrue("the file is still on the person", file.person === person)
        assertEquals(FILE_SIZE, file.byteCount)
        // Read off the main thread: the bytes come from app-private storage.
        val bytes = file.data
        assertNotNull("the file's bytes are readable", bytes)
        assertTrue("the file's bytes are intact", fileBytes().contentEquals(bytes!!))

        assertEquals("the follow-up survived the update", 1, followUps.size)
        val followUp = followUps.single()
        assertFalse(followUp.isCompleted)
        assertTrue("the follow-up is still on the person", followUp.person === person)

        val identifier = FollowUpScheduler.identifier(followUp)
        val pending = AndroidNotificationService(appContext).pendingReminder(identifier)
        assertNotNull("the follow-up's reminder is still pending after the update", pending)
        assertEquals("the reminder still fires when it was set to", followUp.dueDate, pending!!.fireDate)
        assertEquals(FOLLOW_UP_TITLE, pending.body)
    }

    private fun installedVersionCode(appContext: Context): Long {
        val info = packageInfo(appContext)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else legacyVersionCode(info)
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(appContext: Context): PackageInfo =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0)

    @Suppress("DEPRECATION")
    private fun legacyVersionCode(info: PackageInfo): Long = info.versionCode.toLong()

    private companion object {
        const val GIVEN_NAME = "Ines"
        const val FAMILY_NAME = "Moreau"
        const val COMPANY = "Harbor & Pine"
        const val CONTACT_IDENTIFIER = "release-check-contact-1"
        const val NOTE_TITLE = "Release check note"
        const val NOTE_BODY = "Prefers mornings.\nAsk about the Lisbon office."
        const val FILE_TITLE = "Release check file"
        const val FILE_SIZE = 70_000
        const val FOLLOW_UP_TITLE = "Send the release check proposal"

        /** The same bytes every time, every value 0–255, more than one disk block. */
        fun fileBytes(): ByteArray = ByteArray(FILE_SIZE) { index -> ((index * 31 + index / 251) and 0xFF).toByte() }
    }
}
