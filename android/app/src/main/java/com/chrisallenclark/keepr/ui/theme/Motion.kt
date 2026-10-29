package com.chrisallenclark.keepr.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI

/**
 * SwiftUI's animation curves as Compose springs.
 *
 * SwiftUI describes a spring by its `response` (seconds per oscillation) and
 * damping; Compose by stiffness and damping ratio. stiffness = (2π / response)²
 * for a unit mass, so the same numbers produce the same motion.
 */
object Motion {
    /** `.snappy(duration:)` — a quick spring with a hint of bounce. */
    fun <T> snappy(duration: Float = 0.5f): SpringSpec<T> =
        spring(dampingRatio = 0.85f, stiffness = stiffness(duration))

    /** `.smooth` / the default `withAnimation` — no bounce. */
    fun <T> smooth(duration: Float = 0.5f): SpringSpec<T> =
        spring(dampingRatio = 1f, stiffness = stiffness(duration))

    private fun stiffness(response: Float): Float {
        val omega = 2 * PI / response
        return (omega * omega).toFloat()
    }
}
