package com.chrisallenclark.keepr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Follows the system appearance, as the iOS app does. */
@Composable
fun KeeprTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkKeeprColors else LightKeeprColors
    CompositionLocalProvider(
        LocalKeeprColors provides colors,
        LocalKeeprTypography provides DefaultKeeprTypography,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = colors.accent,
            backgroundColor = colors.accent.copy(alpha = 0.3f),
        ),
        content = content,
    )
}

object KeeprTheme {
    val colors: KeeprColors
        @Composable get() = LocalKeeprColors.current

    val typography: KeeprTypography
        @Composable get() = LocalKeeprTypography.current
}
