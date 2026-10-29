package com.chrisallenclark.keepr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.theme.Chrome
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Radius
import com.chrisallenclark.keepr.ui.theme.Spacing

/**
 * `.sheet`: a card that rises over the screen, dims what's behind it, and is
 * dismissed by dragging it down or with the system back gesture.
 *
 * Material's bottom sheet supplies the gesture handling and back behavior; its
 * look is replaced with iOS 26's — 38pt top corners, the grouped background,
 * a small grabber, and a navigation bar with text buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeeprSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KeeprTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        containerColor = colors.groupedBackground,
        contentColor = colors.label,
        scrimColor = Color.Black.copy(alpha = if (colors.isDark) 0.48f else 0.2f),
        dragHandle = { Grabber() },
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
private fun Grabber() {
    Box(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = 5.dp)
                .background(KeeprTheme.colors.tertiaryLabel, CircleShape),
        )
    }
}

/** The bar at the top of a sheet: Cancel, a title, and a confirming action. */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    cancelTitle: String = "Cancel",
    onCancel: (() -> Unit)? = null,
    confirmTitle: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Chrome.navigationBarHeight + Spacing.small)
            .padding(horizontal = 16.dp),
    ) {
        if (onCancel != null) {
            ToolbarTextButton(cancelTitle, onCancel, Modifier.align(Alignment.CenterStart))
        }
        KText(
            title,
            style = KeeprTheme.typography.headline,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center),
        )
        if (confirmTitle != null && onConfirm != null) {
            ToolbarTextButton(
                confirmTitle,
                onConfirm,
                Modifier.align(Alignment.CenterEnd),
                isProminent = true,
                enabled = confirmEnabled,
            )
        }
    }
}

/** Convenience: a sheet whose body is a vertically stacked column. */
@Composable
fun SheetBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = 24.dp), content = content)
}
