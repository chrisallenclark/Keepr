package com.chrisallenclark.keepr.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chrisallenclark.keepr.core.services.LocalReminder
import java.time.Instant

/**
 * Debug builds only: lets CI's emulator script schedule a real reminder from
 * the shell, without the UI and without an instrumentation run (starting one
 * force-stops the app, which would cancel the very alarm being tested).
 *
 *     adb shell am broadcast -n com.chrisallenclark.keepr/.notifications.DebugReminderReceiver \
 *         -a com.chrisallenclark.keepr.debug.SCHEDULE --es id smoke-a --es title "Hi" --ei seconds 20
 *
 * Protected by android.permission.DUMP, which the shell holds and ordinary
 * apps can't. The result line (`am broadcast` prints it) lists what's pending.
 */
class DebugReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = AndroidNotificationService(context)
        val identifier = intent.getStringExtra("id") ?: "debug-smoke"
        when (intent.action) {
            "com.chrisallenclark.keepr.debug.SCHEDULE" -> {
                val seconds = intent.getIntExtra("seconds", 15).toLong()
                service.scheduleNow(
                    LocalReminder(
                        identifier = identifier,
                        title = intent.getStringExtra("title") ?: "Keepr test reminder",
                        body = intent.getStringExtra("body") ?: "Scheduled from the emulator script.",
                        fireDate = Instant.now().plusSeconds(seconds),
                    ),
                )
            }
            "com.chrisallenclark.keepr.debug.CANCEL" -> service.cancelNow(identifier)
            "com.chrisallenclark.keepr.debug.STATUS" -> Unit
            else -> return
        }
        resultData = "status=${service.currentStatus()} exact=${service.canScheduleExact()} " +
            "pending=${service.pendingIdentifiers().joinToString(",")}"
    }
}
