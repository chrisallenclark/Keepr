package com.chrisallenclark.keepr.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.core.domain.SymbolCatalog
import com.chrisallenclark.keepr.ui.components.EmptyState
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.components.KeeprSheet
import com.chrisallenclark.keepr.ui.components.SearchField
import com.chrisallenclark.keepr.ui.components.SheetHeader
import com.chrisallenclark.keepr.ui.components.rememberHaptics
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Spacing
import com.chrisallenclark.keepr.ui.theme.semibold

/**
 * Choose the SF Symbol that stands for a group or a relationship type.
 * Translated from Keepr/DesignSystem/SymbolPicker.swift; the catalogue is
 * core's [SymbolCatalog], and every entry in it has an Android drawable.
 *
 * The choice is held apart from [selection] so Cancel really cancels.
 */
@Composable
fun SymbolPickerSheet(
    selection: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KeeprTheme.colors
    val haptics = rememberHaptics()
    var draft by rememberSaveable { mutableStateOf(selection) }
    var query by rememberSaveable { mutableStateOf("") }
    val categories = SymbolCatalog.visibleCategories(query)

    KeeprSheet(onDismiss = onDismiss) {
        SheetHeader(
            "Choose a Symbol",
            onCancel = onDismiss,
            confirmTitle = "Done",
            onConfirm = {
                onSelect(draft)
                onDismiss()
            },
        )
        SearchField(query, { query = it }, "Search — client, gym, meal, money…")
        Spacer(Modifier.height(Spacing.small))
        if (categories.isEmpty()) {
            EmptyState(
                "No Results for “${query.trim()}”",
                "magnifyingglass",
                description = "Check the spelling or try a new search.",
                modifier = Modifier.padding(vertical = 60.dp),
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // `GridItem(.adaptive(minimum: 56), spacing: 8)` inside 16pt margins.
                val available = maxWidth - 32.dp
                val columns = maxOf(1, ((available + Spacing.small) / (56.dp + Spacing.small)).toInt())
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = Spacing.large + 24.dp),
                ) {
                    categories.forEach { category ->
                        KText(
                            category.title,
                            style = KeeprTheme.typography.subheadline.semibold,
                            color = colors.secondaryLabel,
                            modifier = Modifier.padding(top = Spacing.medium, bottom = Spacing.tight),
                        )
                        category.symbols.chunked(columns).forEach { rowSymbols ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = Spacing.small),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                            ) {
                                rowSymbols.forEach { symbol ->
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        SymbolCell(symbol.name, symbol.name == draft) {
                                            draft = symbol.name
                                            haptics.selection()
                                        }
                                    }
                                }
                                repeat(columns - rowSymbols.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolCell(name: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = KeeprTheme.colors
    Box(
        Modifier
            .size(52.dp)
            .background(if (isSelected) colors.accent else colors.tertiaryFill, CircleShape)
            .selectable(
                selected = isSelected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = SymbolCatalog.readableName(name) },
        contentAlignment = Alignment.Center,
    ) {
        SfSymbol(name, if (isSelected) Color.White else colors.label, KeeprTheme.typography.title3)
    }
}
