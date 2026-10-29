package com.chrisallenclark.keepr.ui.components

import androidx.compose.animation.core.animateFloatAsState
import com.chrisallenclark.keepr.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Radius
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * A raised panel that sits on the grouped background rather than filling a
 * row. iOS `FloatingCard`: the system card color, a hairline border and a
 * shadow that's deliberately almost invisible — which is what reads as
 * "floating" instead of "boxed".
 */
@Composable
fun FloatingCard(
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = KeeprTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    Box(
        modifier = modifier
            .fillMaxWidth()
            // shadow(color: .black.opacity(0.07), radius: 5, y: 2)
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.07f),
                spotColor = Color.Black.copy(alpha = 0.12f),
            )
            .background(colors.secondaryGroupedBackground, shape)
            .border(
                width = if (isHighlighted) 1.5.dp else 0.5.dp,
                color = if (isHighlighted) colors.accent.copy(alpha = 0.45f) else colors.quaternaryLabel,
                shape = shape,
            )
            .padding(Spacing.medium),
        content = content,
    )
}

/**
 * The chevron on a card that opens and closes, turning the same way and at
 * the same speed everywhere (`.snappy(duration: 0.22)`).
 */
@Composable
fun DisclosureChevron(isExpanded: Boolean, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (isExpanded) 0f else -90f, Motion.snappy(0.22f), label = "chevron")
    val caption = KeeprTheme.typography.caption.semibold
    SfSymbol(
        "chevron.down",
        KeeprTheme.colors.secondaryLabel,
        caption,
        modifier = modifier.rotate(rotation),
    )
}
