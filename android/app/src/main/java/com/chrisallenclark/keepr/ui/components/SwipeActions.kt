package com.chrisallenclark.keepr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Motion
import com.chrisallenclark.keepr.ui.theme.medium
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class SwipeAction(
    val title: String,
    val symbolName: String,
    val tint: Color,
    val onAction: () -> Unit,
)

/**
 * `.swipeActions(edge:allowsFullSwipe:)`: drag a row sideways to reveal
 * buttons behind it; drag far enough and the first action on that edge fires
 * on its own, as a full swipe does on iOS. Each action is also exposed to
 * TalkBack, since a hidden gesture is otherwise unreachable.
 */
@Composable
fun SwipeActionsRow(
    modifier: Modifier = Modifier,
    leading: List<SwipeAction> = emptyList(),
    trailing: List<SwipeAction> = emptyList(),
    allowsFullSwipe: Boolean = true,
    /** The row's own fill, which slides over the buttons: the card color, or the plain list's. */
    rowBackground: Color = KeeprTheme.colors.secondaryGroupedBackground,
    /** Starts the row already swiped by this much — for the design gallery's screenshots. */
    previewOffset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val buttonWidth = with(density) { 74.dp.toPx() }
    val offset = remember { Animatable(with(density) { previewOffset.toPx() }) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var width by remember { mutableIntStateOf(0) }

    val leadingOpen = buttonWidth * leading.size
    val trailingOpen = buttonWidth * trailing.size
    val fullSwipe = width * 0.6f

    fun settle(velocity: Float) {
        scope.launch {
            val value = offset.value
            val target = when {
                allowsFullSwipe && leading.isNotEmpty() && value > fullSwipe -> {
                    leading.first().onAction(); haptics.success(); 0f
                }
                allowsFullSwipe && trailing.isNotEmpty() && value < -fullSwipe -> {
                    trailing.first().onAction(); haptics.success(); 0f
                }
                value > leadingOpen / 2 || velocity > 1500f && leading.isNotEmpty() -> leadingOpen
                value < -trailingOpen / 2 || velocity < -1500f && trailing.isNotEmpty() -> -trailingOpen
                else -> 0f
            }
            offset.animateTo(target, Motion.snappy(0.35f))
        }
    }

    fun perform(action: SwipeAction) {
        action.onAction()
        haptics.success()
        scope.launch { offset.animateTo(0f, Motion.snappy(0.35f)) }
    }

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { width = it.width }
            .semantics {
                customActions = (leading + trailing).map { action ->
                    CustomAccessibilityAction(action.title) { perform(action); true }
                }
            },
    ) {
        if (offset.value != 0f) {
            Box(Modifier.matchParentSize()) {
                if (offset.value > 0f && leading.isNotEmpty()) {
                    ActionStrip(leading, Modifier.align(Alignment.CenterStart), offset.value, ::perform)
                }
                if (offset.value < 0f && trailing.isNotEmpty()) {
                    ActionStrip(trailing.reversed(), Modifier.align(Alignment.CenterEnd), -offset.value, ::perform)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(rowBackground)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val next = offset.value + delta
                        val min = if (trailing.isEmpty()) 0f else -width.toFloat()
                        val max = if (leading.isEmpty()) 0f else width.toFloat()
                        scope.launch { offset.snapTo(next.coerceIn(min, max)) }
                    },
                    onDragStopped = { velocity -> settle(velocity) },
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun ActionStrip(
    actions: List<SwipeAction>,
    modifier: Modifier,
    revealed: Float,
    onTap: (SwipeAction) -> Unit,
) {
    val density = LocalDensity.current
    val total = with(density) { revealed.toDp() }
    Row(modifier.width(total).fillMaxHeight()) {
        actions.forEach { action ->
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(action.tint)
                    .clickable { onTap(action) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                if (abs(revealed) > with(density) { 40.dp.toPx() }) {
                    SfSymbol(action.symbolName, Color.White, 22.dp)
                    KText(
                        action.title,
                        style = KeeprTheme.typography.caption.medium,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
