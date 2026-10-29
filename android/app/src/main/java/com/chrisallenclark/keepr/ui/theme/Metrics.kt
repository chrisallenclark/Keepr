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

/** `Theme.Radius` from the iOS app, plus the system's own surfaces. */
object Radius {
    val card = 14.dp
    val chip = 8.dp
    /** An inset-grouped list section on iOS 26 (measured). */
    val insetGroup = 26.dp
    /** The top corners of a sheet on iOS 26. */
    val sheet = 38.dp
    /** A context menu on iOS 26. */
    val menu = 22.dp
}

/** Fixed sizes of iOS 26 system chrome, measured from simulator screenshots. */
object Chrome {
    val navigationBarHeight = 44.dp
    /** Space iOS 26 leaves between the navigation bar and what's pinned under it. */
    val navigationBarBottomGap = 10.dp
    val toolbarButton = 44.dp
    val tabBarHeight = 61.dp
    val tabBarSideMargin = 21.dp
    val tabBarBottomMargin = 21.dp
    val segmentedHeight = 32.dp
    val searchFieldHeight = 44.dp
    /** Room content needs at the bottom so the floating tab bar never covers it. */
    val tabBarClearance = 61.dp + 21.dp + 12.dp
}
