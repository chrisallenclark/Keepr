package com.chrisallenclark.keepr.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import com.chrisallenclark.keepr.ui.theme.KeeprTheme

/**
 * iOS highlights a tapped list row with a gray wash instead of Android's
 * ripple. Fades in fast and out a little slower, like UIKit's.
 */
@Composable
fun Modifier.pressHighlight(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 60 else 250), label = "press")
    val wash: Color = KeeprTheme.colors.fill
    return drawWithContent {
        if (alpha > 0f) drawRect(wash, alpha = alpha * 0.6f)
        drawContent()
    }
}
