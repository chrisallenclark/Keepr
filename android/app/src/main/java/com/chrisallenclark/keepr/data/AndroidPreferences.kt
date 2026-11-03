package com.chrisallenclark.keepr.data

import android.content.Context
import android.content.SharedPreferences
import com.chrisallenclark.keepr.core.persistence.Preferences

/** `UserDefaults` for Android: one SharedPreferences file. */
class AndroidPreferences(private val prefs: SharedPreferences) : Preferences {
    constructor(context: Context, name: String = "keepr") : this(context.getSharedPreferences(name, Context.MODE_PRIVATE))

    override fun stringList(key: String): List<String>? =
        prefs.getString(key, null)?.let { if (it.isEmpty()) emptyList() else it.split('\u001F') }

    override fun setStringList(key: String, value: List<String>) {
        prefs.edit().putString(key, value.joinToString("\u001F")).apply()
    }

    override fun string(key: String): String? = prefs.getString(key, null)

    override fun setString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    override fun bool(key: String): Boolean = prefs.getBoolean(key, false)

    override fun setBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/** Preferences that live only as long as the process — demo runs. */
class MemoryPreferences : Preferences {
    private val values = mutableMapOf<String, Any>()
    @Suppress("UNCHECKED_CAST")
    override fun stringList(key: String): List<String>? = values[key] as? List<String>
    override fun setStringList(key: String, value: List<String>) { values[key] = value }
    override fun string(key: String): String? = values[key] as? String
    override fun setString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    override fun bool(key: String): Boolean = values[key] as? Boolean ?: false
    override fun setBool(key: String, value: Boolean) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}
