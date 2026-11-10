package com.chrisallenclark.keepr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.theme.Chrome
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

/**
 * Where a screen's [EmptyState] sits: centered in the room above the floating
 * tab bar (iOS centers `ContentUnavailableView` in the safe area, which ends
 * above the tab bar). When it doesn't fit — a 360dp phone at a large text
 * size — it scrolls instead of sliding its button under the tab bar.
 */
@Composable
fun EmptyStateArea(
    modifier: Modifier = Modifier,
    bottomClearance: Dp = Chrome.tabBarClearance,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val minHeight = if (constraints.hasBoundedHeight) maxHeight else 0.dp
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = bottomClearance),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}
