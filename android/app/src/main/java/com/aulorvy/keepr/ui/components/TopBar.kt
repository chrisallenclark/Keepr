package com.aulorvy.keepr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aulorvy.keepr.ui.icons.SfSymbol
import com.aulorvy.keepr.ui.theme.Chrome
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Spacing
import com.aulorvy.keepr.ui.theme.bold

/**
 * An iOS 26 navigation bar with whatever is pinned beneath it.
 *
 * The inline title sits centered between round glass toolbar buttons. Anything
 * passed as [pinned] (the search field, the Business/Personal switch) shares
 * the bar's background, which is how SwiftUI draws `.searchable` and a
 * `.safeAreaInset(edge: .top)` with `.background(.bar)`.
 */
@Composable
fun KeeprTopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    background: Color = KeeprTheme.colors.bar,
    pinned: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding(),
    ) {
        NavigationBarRow(
            title,
            Modifier
                .fillMaxWidth()
                .height(Chrome.navigationBarHeight)
                .padding(horizontal = 16.dp),
            leading = leading,
            trailing = trailing,
        )
        if (pinned != null) {
            Spacer(Modifier.height(Chrome.navigationBarBottomGap))
            pinned()
        } else {
            Spacer(Modifier.height(Spacing.small))
        }
    }
}

/**
 * A navigation bar's row: [leading] items, the inline title, [trailing] items.
 *
 * As UIKit lays out an inline title: centered on the bar when it fits there;
 * otherwise centered in the room left between the buttons; and only when even
 * that is too narrow does it shrink (to 80%, `minimumScaleFactor`) and then
 * truncate. A fixed padding on each side would truncate "Relationship Types"
 * on a 360dp phone while space sat unused beside a single button.
 */
@Composable
fun NavigationBarRow(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleStyle: TextStyle = KeeprTheme.typography.headline,
) {
    val color = KeeprTheme.colors.label
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.layoutId(NavigationSlot.Leading), contentAlignment = Alignment.CenterStart) { leading?.invoke() }
            BasicText(
                text = title,
                style = titleStyle.copy(color = color, textAlign = TextAlign.Center),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = titleStyle.fontSize * 0.8f,
                    maxFontSize = titleStyle.fontSize,
                    stepSize = 0.5.sp,
                ),
                modifier = Modifier
                    .layoutId(NavigationSlot.Title)
                    .semantics { heading() },
            )
            Row(
                Modifier.layoutId(NavigationSlot.Trailing),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { trailing?.invoke() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = 8.dp.roundToPx()
        val leadingPlaceable = measurables.first { it.layoutId == NavigationSlot.Leading }.measure(loose)
        val trailingPlaceable = measurables.first { it.layoutId == NavigationSlot.Trailing }
            .measure(loose.copy(maxWidth = (loose.maxWidth - leadingPlaceable.width).coerceAtLeast(0)))
        val leadingEdge = if (leadingPlaceable.width > 0) leadingPlaceable.width + gap else 0
        val trailingEdge = if (trailingPlaceable.width > 0) trailingPlaceable.width + gap else 0
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth
        else leadingEdge + trailingEdge + 400.dp.roundToPx()
        val room = (width - leadingEdge - trailingEdge).coerceAtLeast(0)
        val titlePlaceable = measurables.first { it.layoutId == NavigationSlot.Title }
            .measure(loose.copy(maxWidth = room))
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight
        else maxOf(leadingPlaceable.height, titlePlaceable.height, trailingPlaceable.height)
        layout(width, height) {
            leadingPlaceable.placeRelative(0, (height - leadingPlaceable.height) / 2)
            trailingPlaceable.placeRelative(width - trailingPlaceable.width, (height - trailingPlaceable.height) / 2)
            val centered = (width - titlePlaceable.width) / 2
            val titleX = if (centered >= leadingEdge && centered + titlePlaceable.width <= width - trailingEdge) centered
            else leadingEdge + (room - titlePlaceable.width) / 2
            titlePlaceable.placeRelative(titleX, (height - titlePlaceable.height) / 2)
        }
    }
}

private enum class NavigationSlot { Leading, Title, Trailing }

/**
 * The large-title variant (`.navigationBarTitleDisplayMode(.large)`), as on
 * Search: an empty bar, then the title in 34pt bold under it.
 */
@Composable
fun KeeprLargeTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    background: Color = KeeprTheme.colors.background,
    pinned: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(Chrome.navigationBarHeight))
        KText(
            title,
            style = KeeprTheme.typography.largeTitle.bold,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 11.dp, bottom = 10.dp)
                .semantics { heading() },
        )
        pinned?.invoke(this)
    }
}

/**
 * A round Liquid Glass toolbar button holding one symbol — the iOS 26 look of
 * `ToolbarItem { Button { Label(…, systemImage:) } }`.
 */
@Composable
fun ToolbarButton(
    symbolName: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = KeeprTheme.colors.label,
) {
    Box(
        modifier
            .size(Chrome.toolbarButton)
            .glass(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        SfSymbol(symbolName, tint, 27.dp)
    }
}

/** A glass capsule holding a word — "Cancel", "Done" — as iOS 26 draws text toolbar buttons. */
@Composable
fun ToolbarTextButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isProminent: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Box(
        modifier
            .height(Chrome.toolbarButton)
            .widthIn(min = Chrome.toolbarButton)
            // iOS 26 draws a disabled prominent button as a plain gray
            // capsule, not a faded accent one.
            .glass(
                CircleShape,
                fill = when {
                    isProminent && enabled -> colors.accent
                    isProminent -> colors.secondaryFill.compositeOver(colors.glass)
                    else -> colors.glass
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        KText(
            title,
            style = if (isProminent) typography.headline else typography.body,
            color = when {
                !enabled && isProminent -> colors.secondaryLabel.copy(alpha = colors.secondaryLabel.alpha * 0.75f)
                !enabled -> colors.tertiaryLabel
                isProminent -> Color.White
                else -> colors.label
            },
            maxLines = 1,
        )
    }
}

/**
 * `.searchable`'s field on iOS 26: a 44pt capsule with a magnifying glass.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    prompt: String,
    modifier: Modifier = Modifier,
) {
    val colors = KeeprTheme.colors
    val style = KeeprTheme.typography.body
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(Chrome.searchFieldHeight)
            .background(colors.searchField, CircleShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SfSymbol("magnifyingglass", colors.label, 22.dp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                KText(prompt, style = style, color = colors.secondaryLabel, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = style.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = prompt },
            )
        }
        if (query.isNotEmpty()) {
            SfSymbol(
                "xmark.circle.fill",
                colors.tertiaryLabel,
                20.dp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onQueryChange("") },
                contentDescription = "Clear",
            )
        }
    }
}
