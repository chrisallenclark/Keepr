package com.chrisallenclark.keepr.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.R

/**
 * Draws an SF Symbol by its iOS name, using its Material Symbols stand-in.
 *
 * Names stay the iOS ones everywhere — in code, in stored data (a group's or a
 * type's chosen symbol), in sync later — so the two apps never disagree about
 * which icon a record has. Unknown names fall back to a neutral circle rather
 * than crashing on data written by a newer iOS build.
 */
@Composable
fun SfSymbol(
    name: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(sfSymbolDrawable(name)),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(size),
    )
}

/**
 * Draws a symbol sized the way SwiftUI sizes one inside text: an SF Symbol
 * set in `.font(.caption)` is as tall as caption text. Material glyphs sit
 * inside a box with ~1/12 padding each side, so the box is a little larger
 * than the type size.
 */
@Composable
fun SfSymbol(
    name: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    SfSymbol(name, color, symbolSize(style), modifier, contentDescription)
}

fun symbolSize(style: TextStyle): Dp = (style.fontSize.value * 1.2f).dp

fun sfSymbolDrawable(name: String): Int = sfSymbolTable[name] ?: R.drawable.sf_circle

fun isKnownSfSymbol(name: String): Boolean = name in sfSymbolTable
