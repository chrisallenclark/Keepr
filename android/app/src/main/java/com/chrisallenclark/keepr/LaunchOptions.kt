package com.chrisallenclark.keepr

import android.content.Intent

/**
 * Debug-only launch extras used by the CI screenshot job, the Android twin of
 * the iOS app's `-KeeprScreen` / `-KeeprContext` arguments:
 *
 *     adb shell am start -n com.chrisallenclark.keepr/.MainActivity \
 *         --es keepr.screen people --es keepr.context business
 *
 * In a release build [from] always returns [None], so none of this can be
 * triggered on a client's phone.
 */
data class LaunchOptions(val screen: String?, val context: String?) {
    companion object {
        val None = LaunchOptions(null, null)

        fun from(intent: Intent?): LaunchOptions {
            if (!BuildConfig.DEBUG || intent == null) return None
            return LaunchOptions(
                screen = intent.getStringExtra("keepr.screen"),
                context = intent.getStringExtra("keepr.context")?.takeIf { it != "none" },
            )
        }
    }
}
