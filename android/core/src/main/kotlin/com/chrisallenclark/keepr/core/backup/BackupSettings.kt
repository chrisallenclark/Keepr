package com.chrisallenclark.keepr.core.backup

import com.chrisallenclark.keepr.core.persistence.KeeprStore
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import com.chrisallenclark.keepr.core.persistence.Preferences

/**
 * The preferences a backup carries, listed explicitly: only settings that
 * describe the user's choices and their data travel to another phone.
 *
 * Left out on purpose (see docs/BACKUP_FORMAT.md):
 * - the seen-contacts baseline (`keepr.knownContactIdentifiers`,
 *   `keepr.hasContactBaseline`). Contact ids are per device, so the old
 *   phone's list would make every contact on the new one look new and fill
 *   Catch Up. A restore resets it instead, as Delete All Data does, and the
 *   next look at the address book quietly re-baselines.
 * - whether the system was asked for notification or contacts permission:
 *   that's a fact about this phone's permission state, not a preference.
 * - the pending-reminder bookkeeping: reminders are rebuilt from the data.
 */
object BackupSettings {

    enum class Kind { TEXT, SWITCH, TEXT_LIST }

    /** Key → how it's stored. */
    val included: Map<String, Kind> = linkedMapOf(
        PreferenceKey.CONTEXT_MODE to Kind.TEXT,
        PreferenceKey.GROUP_LABEL to Kind.TEXT,
        PreferenceKey.REMINDERS_ENABLED to Kind.TEXT,
        PreferenceKey.HAS_ONBOARDED to Kind.SWITCH,
        PreferenceKey.PEOPLE_SORT to Kind.TEXT,
        PreferenceKey.DID_DEFAULT_TO_NAME_SORT to Kind.SWITCH,
        // Rhythm: when the "people you're behind on" nudge last went out, so
        // a restored phone doesn't send it again straight away.
        LAST_CADENCE_BACKLOG_NUDGE to Kind.TEXT,
        // Which built-in relationship types were already offered. Without it a
        // restore would bring back built-ins the user had deleted.
        KeeprStore.SEEDED_KEYS_DEFAULT to Kind.TEXT_LIST,
    )

    /** CadenceNotificationScheduler's key (private there). */
    const val LAST_CADENCE_BACKLOG_NUDGE = "keepr.lastCadenceBacklogNudge"

    /** What's currently set, for writing into a backup. Unset values are left out. */
    fun capture(preferences: Preferences): Map<String, SettingValue> {
        val result = LinkedHashMap<String, SettingValue>()
        for ((key, kind) in included) {
            when (kind) {
                Kind.TEXT -> preferences.string(key)?.let { result[key] = SettingValue.Text(it) }
                Kind.SWITCH -> if (preferences.bool(key)) result[key] = SettingValue.Switch(true)
                Kind.TEXT_LIST -> preferences.stringList(key)?.let { result[key] = SettingValue.TextList(it) }
            }
        }
        return result
    }

    /**
     * Makes this phone's included settings match the backup's: values in the
     * backup are set, included settings the backup doesn't have go back to
     * their defaults. Anything not on the list is never touched, whatever a
     * backup contains.
     */
    fun apply(settings: Map<String, SettingValue>, preferences: Preferences) {
        for ((key, kind) in included) {
            val value = settings[key]
            when {
                value is SettingValue.Text && kind == Kind.TEXT -> preferences.setString(key, value.value)
                value is SettingValue.Switch && kind == Kind.SWITCH -> preferences.setBool(key, value.value)
                value is SettingValue.TextList && kind == Kind.TEXT_LIST -> preferences.setStringList(key, value.value)
                else -> preferences.remove(key)
            }
        }
        // Whatever the backup says, a phone that has just been restored is set
        // up: it never goes back to the first-launch screens.
        preferences.setBool(PreferenceKey.HAS_ONBOARDED, true)
    }
}
