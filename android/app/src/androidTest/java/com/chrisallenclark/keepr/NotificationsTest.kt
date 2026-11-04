package com.chrisallenclark.keepr

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chrisallenclark.keepr.core.domain.CadenceNotificationScheduler
import com.chrisallenclark.keepr.core.services.FollowUpReminder
import com.chrisallenclark.keepr.core.services.LocalReminder
import com.chrisallenclark.keepr.core.services.NotificationAuthorizationStatus
import com.chrisallenclark.keepr.notifications.AndroidNotificationService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Real reminders on a real device: AlarmManager fires, the receiver posts,
 * with no activity running at all. The shell-driven checks that need the
 * process gone — killed, force-stopped, rebooted — are in
 * scripts/emulator-smoke.sh.
 */
@RunWith(AndroidJUnit4::class)
class NotificationsTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var service: AndroidNotificationService

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // A freshly installed app that has never been opened can sit in the
        // "never" standby bucket, where alarms wait for the app to be used.
        if (Build.VERSION.SDK_INT >= 28) shell("am set-standby-bucket ${context.packageName} active")
        resetApp()
        manager.cancelAll()
        service = AndroidNotificationService(context)
    }

    @After
    fun tearDown() {
        service.cancelAllNow()
        manager.cancelAll()
    }

    private fun shell(command: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private fun reminder(id: String, title: String = "Priya Raman", body: String = "Send the proposal", inSeconds: Long = 5) =
        LocalReminder(id, title, body, Instant.now().plusSeconds(inSeconds))

    private fun posted(tag: String) = manager.activeNotifications.filter { it.tag == tag }

    private fun waitForPosted(tag: String, timeoutMillis: Long = 90_000): android.service.notification.StatusBarNotification {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            posted(tag).firstOrNull()?.let { return it }
            Thread.sleep(250)
        }
        throw AssertionError("Reminder \"$tag\" was never posted. Pending: ${service.pendingIdentifiers()}")
    }

    private fun waitForRemoved(tag: String, timeoutMillis: Long = 5_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (posted(tag).isEmpty()) return
            Thread.sleep(100)
        }
        throw AssertionError("Reminder \"$tag\" was still showing ${timeoutMillis}ms after it was cancelled")
    }

    @Test
    fun grantedPermissionReadsAsAuthorized() {
        assertEquals(NotificationAuthorizationStatus.AUTHORIZED, runBlocking { service.authorizationStatus() })
        assertTrue(runBlocking { service.requestAuthorization() })
    }

    @Test
    fun reminderPostsWithNoActivityRunning() {
        service.scheduleNow(reminder("followup-TEST-POST"))
        assertEquals(listOf("followup-TEST-POST"), service.pendingIdentifiers())

        val notification = waitForPosted("followup-TEST-POST")
        val extras = notification.notification.extras
        assertEquals("Priya Raman", extras.getCharSequence("android.title")?.toString())
        assertEquals("Send the proposal", extras.getCharSequence("android.text")?.toString())
        assertEquals(AndroidNotificationService.CHANNEL_ID, notification.notification.channelId)
        // Delivered means no longer pending, as on iOS.
        assertTrue(service.pendingIdentifiers().isEmpty())
    }

    @Test
    fun followUpReminderShowsThePersonAndTheirWords() {
        runBlocking {
            service.schedule(FollowUpReminder("followup-TEST-FU", "Jake Martinez", "Send the quote", Instant.now().plusSeconds(5)))
        }
        val extras = waitForPosted("followup-TEST-FU").notification.extras
        assertEquals("Jake Martinez", extras.getCharSequence("android.title")?.toString())
        assertEquals("Send the quote", extras.getCharSequence("android.text")?.toString())
    }

    @Test
    fun cancelledReminderNeverPosts() {
        service.scheduleNow(reminder("followup-TEST-CANCEL", inSeconds = 5))
        runBlocking { service.cancel("followup-TEST-CANCEL") }
        assertTrue(service.pendingIdentifiers().isEmpty())
        Thread.sleep(15_000)
        assertTrue(posted("followup-TEST-CANCEL").isEmpty())
    }

    @Test
    fun cancelRemovesADeliveredReminderToo() {
        service.scheduleNow(reminder("followup-TEST-DELIVERED"))
        waitForPosted("followup-TEST-DELIVERED")
        runBlocking { service.cancel("followup-TEST-DELIVERED") }
        // NotificationManager removes a posted notification asynchronously.
        waitForRemoved("followup-TEST-DELIVERED")
    }

    @Test
    fun reschedulingReplacesTheEarlierReminder() {
        service.scheduleNow(reminder("cadence-TEST", title = "Reach out to Sarah", inSeconds = 3_600))
        service.scheduleNow(reminder("cadence-TEST", title = "2 people to reach out to", inSeconds = 5))
        assertEquals(listOf("cadence-TEST"), service.pendingIdentifiers())
        assertEquals("2 people to reach out to", service.pendingReminder("cadence-TEST")?.title)

        val extras = waitForPosted("cadence-TEST").notification.extras
        assertEquals("2 people to reach out to", extras.getCharSequence("android.title")?.toString())
        assertEquals(1, posted("cadence-TEST").size)
    }

    @Test
    fun pastRemindersAreIgnored() {
        service.scheduleNow(LocalReminder("followup-TEST-PAST", "Late", "Too late", Instant.now().minusSeconds(60)))
        assertTrue(service.pendingIdentifiers().isEmpty())
    }

    @Test
    fun prefixCancelClearsOnlyThatFamily() {
        service.scheduleNow(reminder("cadence-2030-1-1", inSeconds = 3_600))
        service.scheduleNow(reminder("cadence-backlog", inSeconds = 3_600))
        service.scheduleNow(reminder("followup-KEEP", inSeconds = 3_600))
        runBlocking { service.cancelReminders(CadenceNotificationScheduler.IDENTIFIER_PREFIX) }
        assertEquals(listOf("followup-KEEP"), service.pendingIdentifiers())
        runBlocking { service.cancelAll() }
        assertTrue(service.pendingIdentifiers().isEmpty())
    }

    @Test
    fun pendingListSurvivesANewServiceAndRearming() {
        val fireDate = Instant.now().plusSeconds(3_600)
        service.scheduleNow(LocalReminder("followup-TEST-STORE", "Dana Kim", "Coffee", fireDate))

        // What a new process (or the boot receiver) would see.
        val fresh = AndroidNotificationService(context)
        val stored = fresh.pendingReminder("followup-TEST-STORE")
        assertEquals("Dana Kim", stored?.title)
        assertEquals("Coffee", stored?.body)
        assertEquals(fireDate.toEpochMilli(), stored?.fireDate?.toEpochMilli())

        fresh.rearmAll()
        assertEquals(listOf("followup-TEST-STORE"), fresh.pendingIdentifiers())
        fresh.cancelNow("followup-TEST-STORE")
        assertNull(service.pendingReminder("followup-TEST-STORE"))
    }

    @Test
    fun rearmingFiresAReminderThatCameDueWhileAlarmsWereGone() {
        // Schedule, then let it come due without its alarm (as after a
        // force-stop) by delivering nothing and rearming late.
        service.scheduleNow(reminder("followup-TEST-MISSED", inSeconds = 2))
        Thread.sleep(2_500)
        // Whether or not the original alarm already fired, rearming must not
        // post it twice, and the reminder ends up delivered exactly once.
        service.rearmAll()
        waitForPosted("followup-TEST-MISSED")
        Thread.sleep(3_000)
        assertEquals(1, posted("followup-TEST-MISSED").size)
        assertFalse("followup-TEST-MISSED" in service.pendingIdentifiers())
    }
}
