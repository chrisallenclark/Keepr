package com.aulorvy.keepr.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aulorvy.keepr.ui.components.KText
import com.aulorvy.keepr.ui.components.Separator
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.semibold

/**
 * `.alert(title, message:)` with a single OK, as iOS 26 draws it: a centered
 * glass panel, bold title, message, and the button under a hairline.
 */
@Composable
fun InfoAlert(title: String, message: String, onDismiss: () -> Unit) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(290.dp)
                .background(colors.glass, RoundedCornerShape(26.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                KText(title, style = typography.headline, textAlign = TextAlign.Center)
                if (message.isNotEmpty()) {
                    KText(message, style = typography.footnote, color = colors.label, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Separator(start = 0.dp, end = 0.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(remember { MutableInteractionSource() }, null, role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                KText("OK", style = typography.body.semibold, color = colors.accent)
            }
        }
    }
}
