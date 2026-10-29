package com.chrisallenclark.keepr.ui.theme

import androidx.compose.ui.unit.dp

/** `Theme.Spacing` from the iOS app. One iOS point is one dp. */
object Spacing {
    /** Between tightly-related elements — a label and its value. */
    val tight = 4.dp
    /** Between rows of related content. */
    val small = 8.dp
    /** The default gap inside a card or row. */
    val medium = 12.dp
    /** Between distinct blocks in a stack. */
    val large = 20.dp
    /** Between major sections of a screen. */
    val section = 28.dp
}

/** `Theme.Radius` from the iOS app, plus the system's own list radius. */
object Radius {
    val card = 14.dp
    val chip = 8.dp
    /** UIKit's inset-grouped list section corner. */
    val insetGroup = 10.dp
}
