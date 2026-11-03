package com.chrisallenclark.keepr.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.ListMetrics
import com.chrisallenclark.keepr.ui.components.Separator
import com.chrisallenclark.keepr.ui.theme.Chrome
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Radius
import com.chrisallenclark.keepr.ui.theme.Spacing

/*
 * `GroupedSection`, one row at a time, for lists long enough to need a
 * LazyColumn — an address book can hold thousands of cards. Same metrics as
 * GroupedSection: 16pt inset, 26pt corners, hairlines from the text's start.
 */

/** Where a row's text starts after a 36pt avatar and a 12pt gap. */
internal val avatarTextStart: Dp = ListMetrics.rowHorizontal + 36.dp + Spacing.medium

/** One row of an inset-grouped section. [index] and [count] decide the corners and the hairline. */
@Composable
internal fun InsetGroupedRow(
    index: Int,
    count: Int,
    separatorStart: Dp = ListMetrics.rowHorizontal,
    content: @Composable () -> Unit,
) {
    val top = if (index == 0) Radius.insetGroup else 0.dp
    val bottom = if (index == count - 1) Radius.insetGroup else 0.dp
    Column(
        Modifier
            .padding(horizontal = ListMetrics.sectionInset)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
            .background(KeeprTheme.colors.secondaryGroupedBackground),
    ) {
        if (index > 0) Separator(start = separatorStart)
        content()
    }
}

@Composable
internal fun InsetSectionHeader(title: String) {
    KText(
        title,
        style = KeeprTheme.typography.headline,
        color = KeeprTheme.colors.secondaryLabel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ListMetrics.sectionInset + ListMetrics.textInset,
                end = ListMetrics.sectionInset + ListMetrics.textInset,
                bottom = 9.dp,
            )
            .semantics { heading() },
    )
}

@Composable
internal fun InsetSectionFooter(text: String) {
    KText(
        text,
        style = KeeprTheme.typography.footnote,
        color = KeeprTheme.colors.secondaryLabel,
        modifier = Modifier.padding(
            start = ListMetrics.sectionInset + ListMetrics.textInset,
            end = ListMetrics.sectionInset + ListMetrics.textInset,
            top = 8.dp,
        ),
    )
}

/** `ProgressView("…").controlSize(.large)`: a spinner with its label underneath. */
@Composable
internal fun LoadingState(label: String, modifier: Modifier = Modifier) {
    val colors = KeeprTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 3.dp, modifier = Modifier.padding(4.dp))
        KText(label, style = KeeprTheme.typography.body, color = colors.secondaryLabel)
    }
}

/**
 * A sheet's navigation bar with arbitrary items on each side. The title takes
 * the room between them, so three toolbar items never overlap it on a narrow
 * phone.
 */
@Composable
internal fun SheetToolbar(
    title: String,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Chrome.navigationBarHeight + Spacing.small)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        leading()
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            KText(title, style = KeeprTheme.typography.headline, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small), verticalAlignment = Alignment.CenterVertically) {
            trailing()
        }
    }
}

/** Room under the last row, clear of the gesture bar. */
@Composable
internal fun ListBottomClearance() {
    Spacer(Modifier.height(32.dp))
}
