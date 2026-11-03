package com.chrisallenclark.keepr.ui.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chrisallenclark.keepr.ui.components.KText
import com.chrisallenclark.keepr.ui.theme.KeeprTheme

/**
 * SwiftUI's bare `TextEditor` with a placeholder overlaid: free text straight
 * on the sheet, no box, as Quick Capture and Brain Dump use it. The field's
 * accessibility label is [accessibilityLabel] (the placeholder by default).
 */
@Composable
internal fun WritingPad(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = placeholder,
    minHeight: Dp = 220.dp,
    style: TextStyle = KeeprTheme.typography.body,
    focusRequester: FocusRequester? = null,
) {
    val colors = KeeprTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .padding(horizontal = 21.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (value.isEmpty()) KText(placeholder, style = style, color = colors.tertiaryLabel)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight - 16.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .semantics { contentDescription = accessibilityLabel },
        )
    }
}
