package com.aulorvy.keepr.ui.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.aulorvy.keepr.core.domain.SymbolCatalog
import com.aulorvy.keepr.ui.components.KText
import com.aulorvy.keepr.ui.components.ListRow
import com.aulorvy.keepr.ui.components.NavigationBarRow
import com.aulorvy.keepr.ui.icons.SfSymbol
import com.aulorvy.keepr.ui.theme.Chrome
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Spacing
import com.aulorvy.keepr.ui.theme.semibold

/*
 * Pieces shared by the Organize sheets.
 */

/**
 * A sheet's navigation bar with arbitrary items on each side — the
 * `ToolbarItemGroup(placement: .topBarTrailing)` of "+" and "Done" that
 * [com.aulorvy.keepr.ui.components.SheetHeader] has no slot for.
 */
@Composable
internal fun OrganizeHeader(
    title: String,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    NavigationBarRow(
        title,
        Modifier
            .fillMaxWidth()
            .height(Chrome.navigationBarHeight + Spacing.small)
            .padding(horizontal = 16.dp),
        leading = leading,
        trailing = trailing,
    )
}

/**
 * The "Symbol" row of a form: the chosen symbol in the accent color and a
 * chevron. Opens the symbol picker.
 */
@Composable
internal fun SymbolRow(symbolName: String, onClick: () -> Unit) {
    val colors = KeeprTheme.colors
    ListRow(
        modifier = Modifier.semantics {
            contentDescription = "Symbol"
            stateDescription = SymbolCatalog.readableName(symbolName)
        },
        onClick = onClick,
    ) {
        KText("Symbol", Modifier.weight(1f))
        SfSymbol(symbolName, colors.accent, KeeprTheme.typography.title3)
        SfSymbol("chevron.right", colors.tertiaryLabel, KeeprTheme.typography.caption.semibold)
    }
}

/** An orange warning line under a field: "Business already has a type called …". */
@Composable
internal fun WarningRow(text: String) {
    val colors = KeeprTheme.colors
    ListRow {
        SfSymbol("exclamationmark.circle", colors.orange, KeeprTheme.typography.footnote)
        KText(text, style = KeeprTheme.typography.footnote, color = colors.orange, modifier = Modifier.weight(1f))
    }
}
