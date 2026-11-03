package com.chrisallenclark.keepr.core.persistence

/**
 * The slice of `UserDefaults` the logic uses (seeding bookkeeping, the
 * seen-contacts baseline, settings). Android backs it with SharedPreferences;
 * tests with a map.
 */
interface Preferences {
    fun stringList(key: String): List<String>?
    fun setStringList(key: String, value: List<String>)
    fun string(key: String): String?
    fun setString(key: String, value: String?)
    fun bool(key: String): Boolean
    fun setBool(key: String, value: Boolean)
    fun remove(key: String)
}

/** Every stored preference key in one place, matching the iOS `PreferenceKey` names. */
object PreferenceKey {
    const val CONTEXT_MODE = "keepr.contextMode"
    const val HAS_ONBOARDED = "keepr.hasOnboarded"
    const val PEOPLE_SORT = "keepr.peopleSort"
    const val DID_DEFAULT_TO_NAME_SORT = "keepr.didDefaultToNameSort"
    const val REMINDERS_ENABLED = "keepr.remindersEnabled"
    const val GROUP_LABEL = "keepr.groupLabel"
    const val HAS_ASKED_FOR_NOTIFICATIONS = "keepr.hasAskedForNotifications"
}
