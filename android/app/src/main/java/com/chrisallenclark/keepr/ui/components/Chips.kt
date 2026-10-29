package com.chrisallenclark.keepr.ui.components

import androidx.compose.animation.animateColorAsState
import com.chrisallenclark.keepr.ui.theme.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Radius
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.medium
import com.chrisallenclark.keepr.ui.theme.semibold

/** A relationship type, shown small and unobtrusive. iOS `TagChip`. */
@Composable
fun TagChip(
    name: String,
    modifier: Modifier = Modifier,
    symbolName: String? = null,
    isProminent: Boolean = false,
) {
    val colors = KeeprTheme.colors
    val style = KeeprTheme.typography.caption.let { if (isProminent) it.medium else it }
    val tint = if (isProminent) colors.label else colors.secondaryLabel
    Row(
        modifier = modifier
            .background(colors.quaternaryLabel, RoundedCornerShape(Radius.chip))
            .padding(horizontal = Spacing.small, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (symbolName != null) SfSymbol(symbolName, tint, style)
        KText(name, style = style, color = tint)
    }
}

/** The row of tags under a name. Truncates rather than wrapping. iOS `TagRow`. */
@Composable
fun TagRow(tags: List<Pair<String, String?>>, modifier: Modifier = Modifier, limit: Int = 2) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier.semantics { contentDescription = tags.joinToString { it.first } },
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.take(limit).forEach { (name, symbol) -> TagChip(name, symbolName = symbol) }
        if (tags.size > limit) {
            KText(
                "+${tags.size - limit}",
                style = KeeprTheme.typography.caption,
                color = KeeprTheme.colors.tertiaryLabel,
            )
        }
    }
}

@Immutable
data class FilterFacet(
    val id: String,
    val name: String,
    val symbolName: String,
    val count: Int,
)

/**
 * A scrolling row of filter chips with live counts. iOS `FilterChipRow`.
 *
 * Tapping the selected chip clears it, so there's no separate reset to hunt for.
 */
@Composable
fun FilterChipRow(
    title: String,
    facets: List<FilterFacet>,
    selection: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val select = { id: String? ->
        onSelect(id)
        haptics.selection()
    }
    Column(modifier.padding(vertical = Spacing.tight), verticalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        KText(
            title.uppercase(),
            style = KeeprTheme.typography.caption2.semibold,
            color = KeeprTheme.colors.tertiaryLabel,
            modifier = Modifier.padding(horizontal = Spacing.medium),
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = Spacing.medium)),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            FilterChip(
                FilterFacet("", "All", "circle.grid.2x2", 0),
                isSelected = selection == null,
                showsCount = false,
            ) { select(null) }
            facets.forEach { facet ->
                FilterChip(facet, isSelected = selection == facet.id, showsCount = true) {
                    select(if (selection == facet.id) null else facet.id)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    facet: FilterFacet,
    isSelected: Boolean,
    showsCount: Boolean,
    onClick: () -> Unit,
) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    // `.snappy(duration: 0.2)` on iOS.
    val background by animateColorAsState(
        if (isSelected) colors.accent else colors.quaternaryLabel,
        animationSpec = Motion.snappy(0.2f),
        label = "chip",
    )
    val content = if (isSelected) Color.White else colors.label
    val style = typography.subheadline.let { if (isSelected) it.semibold else it }
    Row(
        modifier = Modifier
            .background(background, CircleShape)
            .selectable(
                selected = isSelected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                contentDescription = if (showsCount) "${facet.name}, ${facet.count} people" else facet.name
            }
            .padding(horizontal = Spacing.medium, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfSymbol(facet.symbolName, content, typography.caption2)
        KText(facet.name, style = style, color = content, maxLines = 1)
        if (showsCount) {
            KText(
                "${facet.count}",
                style = style,
                color = if (isSelected) Color.White.copy(alpha = 0.8f) else colors.secondaryLabel,
            )
        }
    }
}
