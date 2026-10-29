package com.chrisallenclark.keepr.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.semibold

/** SwiftUI's button styles as iOS 26 draws them. */
enum class KeeprButtonStyle {
    /** `.borderless` / default in a list: accent text, no background. */
    Plain,
    /** `.bordered`: accent text on a tinted capsule. */
    Bordered,
    /** `.borderedProminent`: white text on an accent capsule. */
    Prominent,
}

/**
 * A button that dims on press the way iOS buttons do, instead of rippling.
 */
@Composable
fun KeeprButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeeprButtonStyle = KeeprButtonStyle.Plain,
    symbolName: String? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    textStyle: TextStyle = KeeprTheme.typography.body,
) {
    val colors = KeeprTheme.colors
    val tint = if (isDestructive) colors.red else colors.accent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.55f else 1f, tween(if (pressed) 50 else 200), label = "pressAlpha")

    val (background, foreground) = when (style) {
        KeeprButtonStyle.Plain -> Color.Transparent to tint
        KeeprButtonStyle.Bordered -> tint.copy(alpha = 0.15f) to tint
        KeeprButtonStyle.Prominent -> tint to Color.White
    }
    val padded = style != KeeprButtonStyle.Plain
    Row(
        modifier
            .alpha(if (enabled) pressAlpha else 0.4f)
            .then(if (padded) Modifier.heightIn(min = 44.dp).background(background, CircleShape) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (padded) Modifier.padding(horizontal = 20.dp, vertical = 10.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (symbolName != null) SfSymbol(symbolName, foreground, textStyle)
        KText(
            title,
            style = if (style == KeeprButtonStyle.Prominent) textStyle.semibold else textStyle,
            color = foreground,
            maxLines = 1,
        )
    }
}
