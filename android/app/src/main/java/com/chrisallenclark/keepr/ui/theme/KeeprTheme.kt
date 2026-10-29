package com.chrisallenclark.keepr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Follows the system appearance, as the iOS app does. */
@Composable
fun KeeprTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkKeeprColors else LightKeeprColors
    CompositionLocalProvider(LocalKeeprColors provides colors, content = content)
}

object KeeprTheme {
    val colors: KeeprColors
        @Composable get() = LocalKeeprColors.current
}
