package com.chrisallenclark.keepr.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * The A–Z strip down the side of People. Touch or drag to jump; one selection
 * haptic per letter crossed. iOS `SectionIndexBar`.
 */
@Composable
fun SectionIndexBar(
    titles: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val currentTitles by rememberUpdatedState(titles)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var height by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf<String?>(null) }

    fun select(y: Float) {
        if (currentTitles.isEmpty() || height == 0) return
        val index = (y / (height.toFloat() / currentTitles.size)).toInt().coerceIn(0, currentTitles.lastIndex)
        val title = currentTitles[index]
        if (title != active) {
            active = title
            currentOnSelect(title)
            haptics.selection()
        }
    }

    Box(
        modifier
            .width(22.dp)
            .padding(vertical = 8.dp)
            .onSizeChanged { height = it.height }
            .clearAndSetSemantics { }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    select(down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            select(change.position.y)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    active = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            titles.forEach { title ->
                KText(
                    title,
                    style = KeeprTheme.typography.caption2.semibold.copy(fontSize = 11.sp),
                    color = KeeprTheme.colors.accent,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
