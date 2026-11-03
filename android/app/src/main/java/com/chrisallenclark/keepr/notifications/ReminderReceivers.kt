package com.chrisallenclark.keepr.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * An alarm went off: post that reminder's notification. Runs without the
 * app's UI — the activity may be long gone — and needs nothing but the
 * pending list, so it's a few milliseconds of work.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidNotificationService.ACTION_FIRE) return
        val identifier = intent.getStringExtra(AndroidNotificationService.EXTRA_IDENTIFIER) ?: return
        AndroidNotificationService(context).deliver(identifier)
    }
}

/**
 * Alarms are forgotten on reboot, and are worth setting again after an
 * update, a clock or time-zone change, or a change in exact-alarm access.
 * The pending list still has every reminder, so set them all again.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED,
            -> {
                val pending = goAsync()
                Thread {
                    try {
                        AndroidNotificationService(context).rearmAll()
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }

    companion object {
        /** `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31), by value. */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
