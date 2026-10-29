package com.chrisallenclark.keepr.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colors the iOS app gets from the system, written down once.
 *
 * iOS supplies these as semantic colors (`systemGroupedBackground`,
 * `secondaryLabel`, …) that switch with the appearance. Android has nothing
 * with the same values, so they're spelled out here from Apple's published
 * values for iOS 17+. Names follow UIKit's so a SwiftUI view translates
 * line by line: `.foregroundStyle(.secondary)` is [secondaryLabel],
 * `Color(.secondarySystemGroupedBackground)` is [secondaryGroupedBackground].
 *
 * The accent is Keepr's own, from `AccentColor.colorset`.
 *
 * Values marked "measured" were sampled from the iOS 26 simulator screenshots
 * the iOS workflow captures, where SwiftUI's rendering differs from UIKit's
 * published constants (hierarchical styles, Liquid Glass surfaces).
 */
@Immutable
data class KeeprColors(
    val isDark: Boolean,

    // Backgrounds
    val background: Color,
    val secondaryBackground: Color,
    val groupedBackground: Color,
    val secondaryGroupedBackground: Color,
    val tertiaryGroupedBackground: Color,

    // Text, and SwiftUI's hierarchical styles (.primary … .quaternary)
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,

    // Fills for controls and chips
    val fill: Color,
    val secondaryFill: Color,
    val tertiaryFill: Color,
    val quaternaryFill: Color,

    val separator: Color,
    val opaqueSeparator: Color,

    /** The `.bar` material behind the navigation bar and context switch. */
    val bar: Color,
    /** Liquid Glass surfaces: the floating tab bar and toolbar buttons. */
    val glass: Color,
    /** The pill behind the selected tab. */
    val glassSelection: Color,
    val segmentTrack: Color,
    val segmentThumb: Color,
    val searchField: Color,

    val accent: Color,

    // System tints used by the app
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val green: Color,
    val blue: Color,
    val gray: Color,
) {
    /** SwiftUI's `.primary` … `.quaternary` foreground styles, by level. */
    fun hierarchical(level: Int): Color = when (level) {
        0 -> label
        1 -> secondaryLabel
        2 -> tertiaryLabel
        else -> quaternaryLabel
    }
}

val LightKeeprColors = KeeprColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    secondaryBackground = Color(0xFFF2F2F7),
    groupedBackground = Color(0xFFF2F2F7),
    secondaryGroupedBackground = Color(0xFFFFFFFF),
    tertiaryGroupedBackground = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    // measured: SwiftUI's .tertiary / .quaternary are black at 25% / 18%
    tertiaryLabel = Color(0x40000000),
    quaternaryLabel = Color(0x2E000000),
    fill = Color(0x33787880),
    secondaryFill = Color(0x29787880),
    tertiaryFill = Color(0x1F767680),
    quaternaryFill = Color(0x14747480),
    separator = Color(0xFFE8E8E8), // measured
    opaqueSeparator = Color(0xFFC6C6C8),
    bar = Color(0xFFF9F9FB), // measured, and the rest of this block
    glass = Color(0xFFFDFDFF),
    glassSelection = Color(0xFFEAEAED),
    segmentTrack = Color(0xFFE7E8E8),
    segmentThumb = Color(0xFFFFFFFF),
    searchField = Color(0xFFE0E2E2),
    accent = Color(0xFF216666),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFFFCC00),
    green = Color(0xFF34C759),
    blue = Color(0xFF007AFF),
    gray = Color(0xFF8E8E93),
)

val DarkKeeprColors = KeeprColors(
    isDark = true,
    background = Color(0xFF000000),
    secondaryBackground = Color(0xFF1C1C1E),
    groupedBackground = Color(0xFF000000),
    secondaryGroupedBackground = Color(0xFF1C1C1E),
    tertiaryGroupedBackground = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x40FFFFFF),
    quaternaryLabel = Color(0x2EFFFFFF),
    fill = Color(0x5C787880),
    secondaryFill = Color(0x52787880),
    tertiaryFill = Color(0x3D767680),
    quaternaryFill = Color(0x2E747480),
    separator = Color(0xFF2A2A2C),
    opaqueSeparator = Color(0xFF38383A),
    bar = Color(0xFF121212),
    glass = Color(0xFF1E1E20),
    glassSelection = Color(0xFF3F3F41),
    segmentTrack = Color(0xFF2A2A2D),
    segmentThumb = Color(0xFF646469),
    searchField = Color(0xFF303132),
    accent = Color(0xFF5AB3AF),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    green = Color(0xFF30D158),
    blue = Color(0xFF0A84FF),
    gray = Color(0xFF8E8E93),
)

val LocalKeeprColors = staticCompositionLocalOf { LightKeeprColors }
