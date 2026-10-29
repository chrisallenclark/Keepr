package com.chrisallenclark.keepr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Radius
import com.chrisallenclark.keepr.ui.theme.Spacing

/*
 * `List { Section { … } }` in `.insetGrouped` and `.plain` styles, iOS 26.
 *
 * Measured on a 390pt iPhone: sections inset 16pt with 26pt corners; rows pad
 * 16pt sideways and 14.5pt vertically; headers are 17pt semibold in the
 * secondary color, 17pt in from the card's edge; footers 13pt; separators are
 * hairlines that start where the row's text starts and stop 16pt short of the
 * edge.
 */

/** Row padding inside a list section. */
object ListMetrics {
    val sectionInset = 16.dp
    val rowHorizontal = 16.dp
    val rowVertical = 14.5.dp
    val textInset = 17.dp
    val rowMinHeight = 52.dp
}

class GroupedSectionScope internal constructor() {
    internal class Row(val separatorStart: Dp, val content: @Composable () -> Unit)

    internal val rows = mutableListOf<Row>()

    /**
     * One row. [separatorStart] is where the hairline above the *next* row
     * begins, measured from the card's leading edge — set it to where this
     * row's text starts, as iOS does.
     */
    fun row(separatorStart: Dp = ListMetrics.rowHorizontal, content: @Composable () -> Unit) {
        rows += Row(separatorStart, content)
    }
}

/** An `.insetGrouped` `Section` with an optional header and footer. */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: GroupedSectionScope.() -> Unit,
) {
    val colors = KeeprTheme.colors
    val scope = GroupedSectionScope().apply(content)
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            SectionHeader(
                header,
                trailing = headerTrailing,
                modifier = Modifier.padding(
                    start = ListMetrics.sectionInset + ListMetrics.textInset,
                    end = ListMetrics.sectionInset + ListMetrics.textInset,
                    bottom = 9.dp,
                ),
            )
        }
        Column(
            Modifier
                .padding(horizontal = ListMetrics.sectionInset)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.insetGroup))
                .background(colors.secondaryGroupedBackground),
        ) {
            scope.rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Separator(start = scope.rows[index - 1].separatorStart)
                }
                row.content()
            }
        }
        if (footer != null) {
            KText(
                footer,
                style = KeeprTheme.typography.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(
                    start = ListMetrics.sectionInset + ListMetrics.textInset,
                    end = ListMetrics.sectionInset + ListMetrics.textInset,
                    top = 8.dp,
                ),
            )
        }
    }
}

/** A section header: "Needs Attention", or a letter in a plain list. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KText(
            title,
            style = KeeprTheme.typography.headline,
            color = KeeprTheme.colors.secondaryLabel,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** A hairline between rows, starting at [start] and stopping 16pt from the end. */
@Composable
fun Separator(modifier: Modifier = Modifier, start: Dp = ListMetrics.rowHorizontal, end: Dp = ListMetrics.rowHorizontal) {
    // One physical pixel, as UIKit draws separators (Dp.Hairline is zero
    // height when used as a size).
    val hairline = (1f / LocalDensity.current.density).dp
    Box(
        modifier
            .padding(start = start, end = end)
            .fillMaxWidth()
            .height(hairline)
            .background(KeeprTheme.colors.separator),
    )
}

/**
 * The standard row frame: padding, minimum height, and an optional tap that
 * behaves like a `.buttonStyle(.plain)` button (no ripple — iOS dims rows on
 * press, which [pressHighlight] reproduces).
 */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    verticalPadding: Dp = ListMetrics.rowVertical,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ListMetrics.rowMinHeight)
            .then(
                if (onClick != null) {
                    Modifier
                        .pressHighlight(interaction)
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = ListMetrics.rowHorizontal, vertical = verticalPadding),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        content = content,
    )
}

/** Vertical gap between sections in a grouped list. */
@Composable
fun SectionSpacer(height: Dp = 26.dp) = Spacer(Modifier.height(height))
