package com.chrisallenclark.keepr.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.chrisallenclark.keepr.ui.theme.KeeprTheme

/**
 * `Text` with an iOS text style and color. Defaults to `.body` in `.primary`.
 */
@Composable
fun KText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = KeeprTheme.typography.body,
    color: Color = KeeprTheme.colors.label,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    strikethrough: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            textAlign = textAlign ?: style.textAlign,
            textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
