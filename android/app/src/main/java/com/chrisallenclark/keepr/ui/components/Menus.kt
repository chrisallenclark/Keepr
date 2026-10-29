package com.chrisallenclark.keepr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Radius

@Immutable
sealed interface MenuEntry {
    data class Action(
        val title: String,
        val symbolName: String? = null,
        val isDestructive: Boolean = false,
        val isChecked: Boolean = false,
        val onClick: () -> Unit,
    ) : MenuEntry

    data object Divider : MenuEntry
}

/**
 * A `Menu` or `.contextMenu`, styled as iOS 26 draws one: a rounded glass
 * panel, 17pt rows with the symbol leading, a checkmark for the chosen option
 * in a picker, red for destructive actions.
 *
 * Material's dropdown provides placement, focus and dismissal; only the look
 * is replaced.
 */
@Composable
fun KeeprMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    entries: List<MenuEntry>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 6.dp),
) {
    val colors = KeeprTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        shape = RoundedCornerShape(Radius.menu),
        containerColor = colors.glass,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        modifier = modifier.widthIn(min = 250.dp),
    ) {
        entries.forEach { entry ->
            when (entry) {
                MenuEntry.Divider -> HorizontalDivider(
                    thickness = 6.dp,
                    color = colors.quaternaryFill,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                is MenuEntry.Action -> {
                    val tint = if (entry.isDestructive) colors.red else colors.label
                    DropdownMenuItem(
                        text = {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                when {
                                    entry.isChecked -> SfSymbol("checkmark", tint, KeeprTheme.typography.body)
                                    entry.symbolName != null -> SfSymbol(entry.symbolName, tint, KeeprTheme.typography.body)
                                }
                                KText(entry.title, color = tint, maxLines = 1, modifier = Modifier.weight(1f))
                            }
                        },
                        onClick = {
                            onDismiss()
                            entry.onClick()
                        },
                        colors = MenuDefaults.itemColors(textColor = tint),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                        modifier = Modifier.heightIn(min = 44.dp),
                    )
                }
            }
        }
    }
}
