package com.chrisallenclark.keepr.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.theme.Chrome
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Motion
import com.chrisallenclark.keepr.ui.theme.medium

/**
 * A segmented `Picker` as iOS 26 draws it: a 32pt capsule track with a thumb
 * that slides to the selection.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KeeprTheme.colors
    val style = KeeprTheme.typography.footnote.medium
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(Chrome.segmentedHeight)
            .background(colors.segmentTrack, CircleShape)
            .padding(2.dp)
            .selectableGroup(),
    ) {
        val segment = maxWidth / options.size
        val thumbX by animateDpAsState(segment * selectedIndex, Motion.snappy(0.3f), label = "thumb")
        Box(
            Modifier
                .offset(x = thumbX)
                .width(segment)
                .fillMaxHeight()
                .shadow(
                    elevation = if (colors.isDark) 0.dp else 2.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.12f),
                )
                .background(colors.segmentThumb, CircleShape),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = index == selectedIndex,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    KText(option, style = style, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The Business ↔ Personal control, pinned under the navigation bar on Today,
 * People and Follow Up. iOS `ContextSwitcher`: a stock segmented picker on the
 * bar background, with a selection haptic.
 */
@Composable
fun ContextSwitcher(
    isPersonal: Boolean,
    onChange: (isPersonal: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    SegmentedControl(
        options = listOf("Business", "Personal"),
        selectedIndex = if (isPersonal) 1 else 0,
        onSelect = { index ->
            val personal = index == 1
            if (personal != isPersonal) {
                onChange(personal)
                haptics.selection()
            }
        },
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = "Context" },
    )
}
