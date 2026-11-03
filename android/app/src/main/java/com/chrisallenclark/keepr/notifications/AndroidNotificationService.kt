package com.chrisallenclark.keepr.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.chrisallenclark.keepr.MainActivity
import com.chrisallenclark.keepr.R
import com.chrisallenclark.keepr.core.services.FollowUpReminder
import com.chrisallenclark.keepr.core.services.LocalReminder
import com.chrisallenclark.keepr.core.services.NotificationAuthorizationStatus
import com.chrisallenclark.keepr.core.services.NotificationScheduling
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/**
 * `LiveNotificationService` for Android: local reminders over AlarmManager.
 *
 * iOS hands a calendar trigger to the system and forgets about it. Android
 * has no such thing, so this keeps the same contract with three parts:
 *
 * - **A pending list** in app-private SharedPreferences (`keepr.reminders`):
 *   identifier, title, body and fire date of everything scheduled. It is what
 *   iOS calls `pendingNotificationRequests()`, and what survives a reboot.
 * - **One alarm per reminder.** Exact (`setExactAndAllowWhileIdle`) when the
 *   user allows exact alarms — always below Android 12, and on 12+ when
 *   "Alarms & reminders" access is on — otherwise inexact
 *   (`setAndAllowWhileIdle`), which Doze may hold back by some minutes. Keepr
 *   never asks for exact-alarm access: a 9am nudge arriving at 9:07 is fine,
 *   and `USE_EXACT_ALARM` is reserved by Play policy for alarm-clock apps.
 * - **Re-arming** from the pending list after boot, an app update, a clock
 *   or time-zone change, and every launch (which also recovers from a
 *   force-stop, which wipes an app's alarms). See docs/NOTIFICATIONS.md.
 *
 * Every operation is synchronous and quick (a preference write and an alarm
 * call); the suspending [NotificationScheduling] methods run them off the
 * main thread, and the receivers call them directly.
 */
class AndroidNotificationService(context: Context) : NotificationScheduling {

    private val app: Context = context.applicationContext
    private val prefs: SharedPreferences = app.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
    private val alarms: AlarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val manager: NotificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Authorization

    override suspend fun authorizationStatus(): NotificationAuthorizationStatus = currentStatus()

    /**
     * Below Android 13 notifications are on unless the user turned them off,
     * so there is no "not determined". From 13 the runtime permission decides;
     * the system doesn't say whether it was ever asked, so the first request
     * is remembered here to tell "not asked" from "said no".
     */
    fun currentStatus(): NotificationAuthorizationStatus {
        if (NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            return NotificationAuthorizationStatus.AUTHORIZED
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasPostPermission() && !prefs.getBoolean(KEY_ASKED, false)) {
            return NotificationAuthorizationStatus.NOT_DETERMINED
        }
        return NotificationAuthorizationStatus.DENIED
    }

    /**
     * Shows the system prompt (Android 13+) through the activity that
     * registered with [attach]. Below 13, or once the user has said no twice,
     * the system shows nothing and this returns the current state.
     */
    override suspend fun requestAuthorization(): Boolean {
        if (currentStatus() == NotificationAuthorizationStatus.AUTHORIZED) return true
        if (Build.VERSION.SDK_INT < 33) return false
        val granted = withContext(Dispatchers.Main) {
            val launcher = permissionLauncher ?: return@withContext false
            prefs.edit().putBoolean(KEY_ASKED, true).apply()
            val result = CompletableDeferred<Boolean>()
            pendingPermission?.complete(false)
            pendingPermission = result
            try {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (error: IllegalStateException) {
                pendingPermission = null
                return@withContext false
            }
            result.await()
        }
        return granted && currentStatus() == NotificationAuthorizationStatus.AUTHORIZED
    }

    private fun hasPostPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // Scheduling

    override suspend fun schedule(reminder: FollowUpReminder) {
        scheduleReminder(reminder.toLocalReminder())
    }

    override suspend fun scheduleReminder(reminder: LocalReminder) = withContext(Dispatchers.IO) {
        scheduleNow(reminder)
    }

    override suspend fun cancel(identifier: String) = withContext(Dispatchers.IO) {
        cancelNow(identifier)
    }

    override suspend fun cancelReminders(withPrefix: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            pendingIdentifiers().filter { it.startsWith(withPrefix) }.forEach { removePending(it) }
        }
    }

    /** Like iOS `removeAllPendingNotificationRequests`: what's already showing stays. */
    override suspend fun cancelAll() = withContext(Dispatchers.IO) {
        cancelAllNow()
    }

    /**
     * Replaces any reminder with this identifier, then — as on iOS — drops it
     * if its moment has passed or notifications aren't allowed.
     */
    fun scheduleNow(reminder: LocalReminder) = synchronized(lock) {
        cancelNow(reminder.identifier)
        if (!reminder.fireDate.isAfter(Instant.now())) return@synchronized
        if (currentStatus() != NotificationAuthorizationStatus.AUTHORIZED) return@synchronized
        prefs.edit().putString(PENDING_PREFIX + reminder.identifier, encode(reminder)).commit()
        arm(reminder.identifier, reminder.fireDate)
    }

    /** Removes the pending reminder and any delivered one with this identifier. */
    fun cancelNow(identifier: String) = synchronized(lock) {
        removePending(identifier)
        manager.cancel(identifier, NOTIFICATION_ID)
    }

    fun cancelAllNow() = synchronized(lock) {
        pendingIdentifiers().forEach { removePending(it) }
    }

    /** iOS `pendingNotificationRequests()`, by identifier. */
    fun pendingIdentifiers(): List<String> =
        prefs.all.keys.filter { it.startsWith(PENDING_PREFIX) }.map { it.removePrefix(PENDING_PREFIX) }.sorted()

    fun pendingReminder(identifier: String): LocalReminder? =
        prefs.getString(PENDING_PREFIX + identifier, null)?.let { decode(identifier, it) }

    /**
     * Sets every pending reminder's alarm again. Alarms don't survive a
     * reboot or a force-stop; the pending list does. A reminder that came due
     * while the phone was off (or the app stopped) is delivered now, unless
     * it's over a day stale — then it's dropped, as old news.
     */
    fun rearmAll(now: Instant = Instant.now()) = synchronized(lock) {
        for (identifier in pendingIdentifiers()) {
            val reminder = pendingReminder(identifier)
            if (reminder == null) {
                removePending(identifier)
                continue
            }
            if (reminder.fireDate.isBefore(now.minus(MISSED_GRACE))) {
                removePending(identifier)
                continue
            }
            arm(identifier, if (reminder.fireDate.isAfter(now)) reminder.fireDate else now)
        }
    }

    // Delivery

    /** Called by [ReminderReceiver] when an alarm goes off. */
    fun deliver(identifier: String) {
        val reminder = synchronized(lock) {
            val pending = pendingReminder(identifier) ?: return
            prefs.edit().remove(PENDING_PREFIX + identifier).commit()
            pending
        }
        post(reminder)
    }

    @SuppressLint("MissingPermission")
    private fun post(reminder: LocalReminder) {
        if (!hasPostPermission() || !NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        ensureChannel()
        val open = Intent(app, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .setData(reminderUri(reminder.identifier))
            .putExtra(EXTRA_IDENTIFIER, reminder.identifier)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(
            app,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // The person's name and the user's own words — nothing generated.
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.sf_bell_fill)
            .setContentTitle(reminder.title)
            .setContentText(reminder.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(reminder.fireDate.toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            manager.notify(reminder.identifier, NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            Log.w(TAG, "Not allowed to post a reminder.")
        }
    }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Follow-ups and rhythms you asked Keepr to remind you about."
        }
        manager.createNotificationChannel(channel)
    }

    // Alarms

    /** Whether alarms can be exact: always below Android 12, by user choice from 12. */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    @SuppressLint("ScheduleExactAlarm", "MissingPermission")
    private fun arm(identifier: String, at: Instant) {
        val operation = alarmIntent(identifier, create = true) ?: return
        val trigger = at.toEpochMilli()
        if (canScheduleExact()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
                return
            } catch (error: SecurityException) {
                // Access was withdrawn between the check and the call; fall through.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
    }

    private fun removePending(identifier: String) {
        alarmIntent(identifier, create = false)?.let { operation ->
            alarms.cancel(operation)
            operation.cancel()
        }
        prefs.edit().remove(PENDING_PREFIX + identifier).commit()
    }

    /** One PendingIntent per identifier: the data URI makes each distinct. */
    private fun alarmIntent(identifier: String, create: Boolean): PendingIntent? {
        val intent = Intent(app, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(reminderUri(identifier))
            .putExtra(EXTRA_IDENTIFIER, identifier)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(app, 0, intent, flags)
    }

    // Activity hookup

    /**
     * Registers the permission prompt with [activity]. Call from `onCreate`;
     * the registration is dropped when the activity is destroyed.
     */
    fun attach(activity: ComponentActivity) {
        val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermission?.complete(granted)
            pendingPermission = null
        }
        permissionLauncher = launcher
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY && permissionLauncher === launcher) {
                    permissionLauncher = null
                    pendingPermission?.complete(false)
                    pendingPermission = null
                }
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        const val STORE_NAME = "keepr.reminders"
        const val ACTION_FIRE = "com.chrisallenclark.keepr.action.REMINDER"
        const val ACTION_OPEN = "com.chrisallenclark.keepr.action.OPEN_REMINDER"
        const val EXTRA_IDENTIFIER = "keepr.reminder"
        /** Every reminder posts with its identifier as the tag and this id. */
        const val NOTIFICATION_ID = 1

        private const val TAG = "KeeprReminders"
        private const val PENDING_PREFIX = "pending:"
        private const val KEY_ASKED = "meta.askedForPermission"
        private val MISSED_GRACE: Duration = Duration.ofHours(24)

        /** Serializes every read-modify-write of the pending list across threads and receivers. */
        private val lock = Any()

        // Process-wide, because the service is built once per process but the
        // activity that can show the prompt comes and goes.
        @Volatile private var permissionLauncher: ActivityResultLauncher<String>? = null
        @Volatile private var pendingPermission: CompletableDeferred<Boolean>? = null

        /** The identifier of a reminder the user just tapped, for the UI to open. */
        val openedReminder = MutableStateFlow<String?>(null)

        private fun reminderUri(identifier: String): Uri = Uri.fromParts("keepr-reminder", identifier, null)

        private fun encode(reminder: LocalReminder): String = JSONObject()
            .put("title", reminder.title)
            .put("body", reminder.body)
            .put("fireDate", reminder.fireDate.toEpochMilli())
            .toString()

        private fun decode(identifier: String, json: String): LocalReminder? = try {
            val value = JSONObject(json)
            LocalReminder(
                identifier = identifier,
                title = value.getString("title"),
                body = value.getString("body"),
                fireDate = Instant.ofEpochMilli(value.getLong("fireDate")),
            )
        } catch (error: org.json.JSONException) {
            null
        }

        /** Reads the identifier off a notification tap, if this intent is one. */
        fun reminderIdentifier(intent: Intent?): String? =
            intent?.takeIf { it.action == ACTION_OPEN }?.getStringExtra(EXTRA_IDENTIFIER)
    }
}
