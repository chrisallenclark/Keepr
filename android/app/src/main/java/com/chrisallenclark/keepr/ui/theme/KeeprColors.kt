package com.chrisallenclark.keepr.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colors the iOS app gets from the system, written down once.
 *
 * iOS supplies these as semantic colors (`systemGroupedBackground`,
 * `secondaryLabel`, …) that switch with the appearance. Android has no
 * equivalent with the same values, so they're spelled out here from Apple's
 * published values. The accent is Keepr's own, from `AccentColor.colorset`.
 *
 * Phase 0 carries only what the placeholder screen draws; the full palette
 * arrives with the design system.
 */
@Immutable
data class KeeprColors(
    val groupedBackground: Color,
    val secondaryGroupedBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val accent: Color,
)

val LightKeeprColors = KeeprColors(
    groupedBackground = Color(0xFFF2F2F7),
    secondaryGroupedBackground = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    accent = Color(0xFF216666),
)

val DarkKeeprColors = KeeprColors(
    groupedBackground = Color(0xFF000000),
    secondaryGroupedBackground = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    accent = Color(0xFF5AB3AF),
)

val LocalKeeprColors = staticCompositionLocalOf { LightKeeprColors }
