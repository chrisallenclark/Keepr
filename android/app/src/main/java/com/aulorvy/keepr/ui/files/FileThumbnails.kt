package com.aulorvy.keepr.ui.files

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aulorvy.keepr.core.model.PersonFile
import com.aulorvy.keepr.core.model.PersonFileKind
import com.aulorvy.keepr.ui.icons.SfSymbol
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Radius

/** Decodes a stored thumbnail once per byte array. */
@Composable
fun rememberThumbnail(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
    bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
}

/**
 * iOS `PersonFileThumbnail`: the square preview on the profile strip and in
 * rows. Falls back to the file-kind icon rather than an empty box, so a PDF
 * without a renderable first page still reads as a document.
 */
@Composable
fun PersonFileThumbnail(file: PersonFile, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    FileThumbnail(file.thumbnailData, file.kind, size, modifier)
}

@Composable
fun FileThumbnail(thumbnail: ByteArray?, kind: PersonFileKind, size: Dp, modifier: Modifier = Modifier) {
    val colors = KeeprTheme.colors
    val shape = RoundedCornerShape(Radius.chip)
    val bitmap = rememberThumbnail(thumbnail)
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier.size(size).clip(shape).background(colors.quaternaryLabel),
            contentAlignment = Alignment.Center,
        ) {
            SfSymbol(kind.symbolName, colors.secondaryLabel, KeeprTheme.typography.title3)
        }
    }
}
