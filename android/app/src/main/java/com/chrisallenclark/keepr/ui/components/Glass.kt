package com.chrisallenclark.keepr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.theme.KeeprTheme

/**
 * iOS 26's Liquid Glass, as far as a flat surface can take it: the measured
 * fill, a hairline rim and a soft lift. The real material also blurs and
 * refracts what's behind it; here the fill is nearly opaque instead, which
 * reads the same in a still frame and keeps text behind it from showing
 * through sharp.
 */
@Composable
fun Modifier.glass(shape: Shape, fill: Color = KeeprTheme.colors.glass): Modifier {
    val isDark = KeeprTheme.colors.isDark
    return this
        .shadow(
            elevation = 10.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.4f else 0.10f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.4f else 0.12f),
        )
        .background(fill.copy(alpha = 0.97f), shape)
        .border(
            width = 0.5.dp,
            color = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.7f),
            shape = shape,
        )
}
