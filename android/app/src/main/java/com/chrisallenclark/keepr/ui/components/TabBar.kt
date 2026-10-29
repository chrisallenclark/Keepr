package com.chrisallenclark.keepr.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.Chrome
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Motion
import com.chrisallenclark.keepr.ui.theme.semibold

/** A tab. iOS 26 draws every tab's symbol in its `.fill` variant, selected or not. */
@Immutable
data class TabItem(val title: String, val symbolName: String)

/**
 * The iOS 26 floating tab bar: a glass capsule above the home indicator, with
 * a pill that slides to the selected tab.
 *
 * Geometry is measured from the iOS app on a 390pt-wide iPhone: 61pt tall,
 * 21pt from the sides and bottom, a 92pt pill inset 4pt. The pill is wider than
 * a quarter of the bar, so pill centers are spread evenly between the two
 * ends rather than centered in equal slots — that's what iOS does too.
 *
 * iOS sits the bar 21pt above the screen's bottom edge, over the home
 * indicator. Android's gesture handle is the same idea, so the bar keeps that
 * margin when gesture navigation is on; with three-button navigation it sits
 * just above the buttons instead.
 */
@Composable
fun KeeprTabBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KeeprTheme.colors
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val navigationInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val bottom = if (navigationInset <= 32.dp) {
        maxOf(Chrome.tabBarBottomMargin, navigationInset)
    } else {
        navigationInset + 8.dp
    }
    BoxWithConstraints(
        modifier
            .padding(
                start = Chrome.tabBarSideMargin,
                end = Chrome.tabBarSideMargin,
                bottom = bottom,
            )
            .fillMaxWidth()
            .height(Chrome.tabBarHeight)
            .glass(CircleShape),
    ) {
        val inset = 4.dp
        val pillWidth = (maxWidth - inset * 2) / items.size * 1.085f
        val step = if (items.size > 1) (maxWidth - inset * 2 - pillWidth) / (items.size - 1) else 0.dp
        val pillX by animateDpAsState(inset + step * selectedIndex, Motion.snappy(0.35f), label = "tabPill")

        Box(
            Modifier
                .offset(x = pillX)
                .padding(vertical = inset)
                .width(pillWidth)
                .fillMaxHeight()
                .background(colors.glassSelection, CircleShape),
        )

        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            val tint by animateColorAsState(
                if (isSelected) colors.accent else colors.label,
                Motion.snappy(0.25f),
                label = "tabTint",
            )
            Column(
                modifier = Modifier
                    .offset(x = inset + step * index)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .selectable(
                        selected = isSelected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) {
                        if (!isSelected) haptics.selection()
                        onSelect(index)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SfSymbol(item.symbolName, tint, 28.dp)
                KText(
                    item.title,
                    style = KeeprTheme.typography.caption2.semibold.copy(fontSize = 10.sp, letterSpacing = 0.sp),
                    color = tint,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}
