package com.chrisallenclark.keepr

import androidx.compose.ui.graphics.toArgb
import com.chrisallenclark.keepr.ui.theme.DarkKeeprColors
import com.chrisallenclark.keepr.ui.theme.LightKeeprColors
import org.junit.Assert.assertEquals
import org.junit.Test

/** The accent must stay identical to the iOS `AccentColor.colorset`. */
class ThemeColorsTest {

    @Test
    fun accentMatchesIosAssetCatalog() {
        // sRGB components from Keepr/Assets.xcassets/AccentColor.colorset.
        assertEquals(argb(0.129, 0.400, 0.400), LightKeeprColors.accent.toArgb())
        assertEquals(argb(0.353, 0.702, 0.686), DarkKeeprColors.accent.toArgb())
    }

    private fun argb(red: Double, green: Double, blue: Double): Int {
        fun channel(value: Double) = Math.round(value * 255).toInt()
        return (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }
}
