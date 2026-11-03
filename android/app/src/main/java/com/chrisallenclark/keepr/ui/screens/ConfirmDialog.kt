package com.chrisallenclark.keepr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.Separator
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * `.confirmationDialog` for a destructive action: an action sheet rising from
 * the bottom with the title and message, the red action, and Cancel apart.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.fillMaxWidth().background(colors.glass, RoundedCornerShape(14.dp))) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        KText(title, style = typography.footnote.semibold, color = colors.secondaryLabel, textAlign = TextAlign.Center)
                        KText(message, style = typography.footnote, color = colors.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                    Separator(start = 0.dp, end = 0.dp)
                    SheetButton(confirmTitle, isDestructive = true) {
                        onDismiss()
                        onConfirm()
                    }
                }
                Box(Modifier.fillMaxWidth().background(colors.glass, RoundedCornerShape(14.dp))) {
                    SheetButton("Cancel", isBold = true, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SheetButton(title: String, isDestructive: Boolean = false, isBold: Boolean = false, onClick: () -> Unit) {
    val colors = KeeprTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 57.dp)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KText(
            title,
            style = KeeprTheme.typography.title3.let { if (isBold) it.semibold else it },
            color = if (isDestructive) colors.red else colors.accent,
        )
    }
}
