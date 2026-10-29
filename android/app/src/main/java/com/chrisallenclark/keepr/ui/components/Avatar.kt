package com.chrisallenclark.keepr.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.medium

/**
 * A person's photo, or their initials when there isn't one.
 *
 * Initials sit on a neutral fill rather than a color assigned per person —
 * a wall of randomly colored circles is the fastest way to make a contact list
 * look like a toy.
 */
enum class AvatarSize(val diameter: Dp) {
    Small(36.dp),
    Medium(44.dp),
    Large(84.dp),
}

@Composable
fun Avatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.Medium,
    imageData: ByteArray? = null,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    val bitmap = remember(imageData) {
        imageData?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val base = modifier
        .size(size.diameter)
        .clip(CircleShape)
        .clearAndSetSemantics { }

    if (bitmap != null) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = base)
        return
    }

    val style: TextStyle = when (size) {
        AvatarSize.Small -> typography.subheadline
        AvatarSize.Medium -> typography.headline
        AvatarSize.Large -> typography.largeTitle
    }.medium
    Box(base.background(colors.quaternaryLabel), contentAlignment = Alignment.Center) {
        KText(
            text = initials,
            style = style,
            color = colors.secondaryLabel,
            maxLines = 1,
            modifier = Modifier.padding(size.diameter * 0.2f),
        )
    }
}
