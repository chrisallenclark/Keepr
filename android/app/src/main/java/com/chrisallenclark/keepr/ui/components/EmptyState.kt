package com.chrisallenclark.keepr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.bold

/**
 * `ContentUnavailableView`: a large gray symbol, a bold title, a description,
 * and optional actions, centered.
 */
@Composable
fun EmptyState(
    title: String,
    symbolName: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SfSymbol(symbolName, colors.secondaryLabel, 64.dp)
        Spacer(Modifier.height(14.dp))
        KText(title, style = typography.title2.bold, textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(Modifier.height(6.dp))
            KText(
                description,
                style = typography.body,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
        if (actions != null) {
            Spacer(Modifier.height(16.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = actions,
            )
        }
    }
}
