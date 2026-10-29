package com.chrisallenclark.keepr.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.chrisallenclark.keepr.R

/**
 * Inter stands in for SF Pro, which Apple licenses for its platforms only.
 *
 * SF switches from its Text to its Display design at 20pt; Inter ships the same
 * split as Inter and Inter Display, so each style uses the cut iOS would.
 */
val InterText = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val InterDisplay = FontFamily(
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)

/**
 * The iOS Dynamic Type styles at the default ("Large") size: point size and
 * Apple's tracking, one-for-one with SwiftUI's `Font.TextStyle`.
 *
 * No line height is set. SwiftUI lays text out at the font's natural height
 * (SF's ascender + descender, ~1.19× the size) rather than the leading Apple
 * publishes for each style, and Inter's natural height is ~1.21×, so leaving
 * it alone is what makes rows and chips come out the same height.
 * Weights are applied the SwiftUI way — `.font(.subheadline.weight(.semibold))`
 * is `typography.subheadline.semibold`.
 */
@Immutable
data class KeeprTypography(
    val largeTitle: TextStyle,
    val title: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val caption2: TextStyle,
)

private fun iosStyle(size: Float, trackingPt: Float, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = if (size >= 20f) InterDisplay else InterText,
        fontWeight = weight,
        fontSize = size.sp,
        letterSpacing = tracking(trackingPt, size),
    )

/** Apple publishes tracking in points; Compose wants it relative to size. */
private fun tracking(points: Float, size: Float): TextUnit = (points / size).em

val DefaultKeeprTypography = KeeprTypography(
    largeTitle = iosStyle(34f, 0.40f),
    title = iosStyle(28f, 0.38f),
    title2 = iosStyle(22f, -0.26f),
    title3 = iosStyle(20f, -0.45f),
    headline = iosStyle(17f, -0.43f, FontWeight.SemiBold),
    body = iosStyle(17f, -0.43f),
    callout = iosStyle(16f, -0.31f),
    subheadline = iosStyle(15f, -0.23f),
    footnote = iosStyle(13f, -0.08f),
    caption = iosStyle(12f, 0f),
    caption2 = iosStyle(11f, 0.06f),
)

val TextStyle.medium: TextStyle get() = copy(fontWeight = FontWeight.Medium)
val TextStyle.semibold: TextStyle get() = copy(fontWeight = FontWeight.SemiBold)
val TextStyle.bold: TextStyle get() = copy(fontWeight = FontWeight.Bold)

val LocalKeeprTypography = staticCompositionLocalOf { DefaultKeeprTypography }
