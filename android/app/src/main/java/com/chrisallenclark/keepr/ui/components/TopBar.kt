package com.chrisallenclark.keepr.ui.components

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.Chrome
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.bold

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
        Box(
            Modifier
                .fillMaxWidth()
                .height(Chrome.navigationBarHeight)
                .padding(horizontal = 16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterStart)) { leading?.invoke() }
            KText(
                title,
                style = KeeprTheme.typography.headline,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = Chrome.toolbarButton + 8.dp)
                    .semantics { heading() },
            )
            Box(Modifier.align(Alignment.CenterEnd)) { trailing?.invoke() }
        }
        if (pinned != null) {
            pinned()
        } else {
            Spacer(Modifier.height(Spacing.small))
        }
    }
}

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
                .padding(top = 4.dp, bottom = 10.dp)
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
            .glass(CircleShape, fill = if (isProminent) colors.accent else colors.glass)
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
