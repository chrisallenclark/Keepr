package com.chrisallenclark.keepr.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The iOS app's three haptics, mapped to the closest Android feedback.
 *
 * Feedback is used only where it clarifies that something happened —
 * completing a follow-up, saving, switching context. Never decorative.
 * Newer constants are used where the device has them; older devices get the
 * nearest one they support.
 */
class Haptics internal constructor(private val view: View) {

    /** `UISelectionFeedbackGenerator` — a detent, for changing a selection. */
    fun selection() {
        val constant = if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }

    /** `UINotificationFeedbackGenerator.success` — something was done. */
    fun success() {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    /** `UIImpactFeedbackGenerator(style: .light)`. */
    fun light() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
