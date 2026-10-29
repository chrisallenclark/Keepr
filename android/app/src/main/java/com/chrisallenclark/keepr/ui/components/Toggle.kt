package com.chrisallenclark.keepr.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Motion

/**
 * `Toggle` as iOS 26 draws it: a wide capsule that turns green, with a pill
 * thumb that slides across.
 */
@Composable
fun KeeprToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = KeeprTheme.colors
    val haptics = rememberHaptics()
    val track by animateColorAsState(if (checked) colors.green else colors.fill, Motion.smooth(0.25f), label = "track")
    val thumbX by animateDpAsState(if (checked) 63.dp - 39.dp - 2.dp else 2.dp, Motion.snappy(0.3f), label = "thumb")
    Box(
        modifier
            .size(width = 63.dp, height = 28.dp)
            .background(track, CircleShape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onCheckedChange(it)
                haptics.light()
            },
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .padding(vertical = 2.dp)
                .size(width = 39.dp, height = 24.dp)
                .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(0.1f), spotColor = Color.Black.copy(0.15f))
                .background(Color.White, CircleShape),
        )
    }
}
