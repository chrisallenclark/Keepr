package com.chrisallenclark.keepr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.chrisallenclark.keepr.core.model.KeeprCalendar
import com.chrisallenclark.keepr.ui.icons.SfSymbol
import com.chrisallenclark.keepr.ui.theme.KeeprTheme
import com.chrisallenclark.keepr.ui.theme.Radius
import com.chrisallenclark.keepr.ui.theme.semibold
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * The controls a SwiftUI `Form` is built from, as rows for [GroupedSection].
 */

/**
 * `TextField` in a form row: plain text, gray placeholder, no box. Grows to
 * [maxLines] for `axis: .vertical` fields.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = KeeprTheme.typography.body,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    focusRequester: FocusRequester? = null,
    onSubmit: (() -> Unit)? = null,
    /** `SecureField`: pass `PasswordVisualTransformation()` with `KeyboardType.Password`. */
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = KeeprTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = ListMetrics.rowMinHeight)
            .padding(horizontal = ListMetrics.rowHorizontal, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) KText(placeholder, style = style, color = colors.tertiaryLabel, maxLines = maxLines)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent),
            minLines = minLines,
            maxLines = maxLines,
            singleLine = maxLines == 1,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                keyboardType = keyboardType,
                imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .semantics { contentDescription = placeholder },
        )
    }
}

/** `LabeledContent`: a title with a secondary value trailing. */
@Composable
fun LabeledRow(title: String, value: String, modifier: Modifier = Modifier, detail: String? = null, onClick: (() -> Unit)? = null) {
    val colors = KeeprTheme.colors
    ListRow(modifier, onClick = onClick) {
        // Both halves fill, so the value hugs the trailing edge whatever the
        // title's length. (A title that only wrapped its text left the rest of
        // the row empty after the value, which floated mid-row.)
        KText(title, Modifier.weight(1f))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            KText(value, color = colors.secondaryLabel, textAlign = TextAlign.End)
            if (detail != null) {
                KText(detail, style = KeeprTheme.typography.caption2, color = colors.tertiaryLabel, textAlign = TextAlign.End)
            }
        }
    }
}

/**
 * `Picker` in its default form style: the title, the current choice in gray
 * and an up/down chevron; tapping opens a menu with a checkmark on the choice.
 */
@Composable
fun <T> FormMenuPicker(
    title: String,
    options: List<T>,
    selection: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    symbol: ((T) -> String?)? = null,
) {
    val colors = KeeprTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        ListRow(onClick = { expanded = true }) {
            KText(title, Modifier.weight(1f))
            KText(label(selection), color = colors.secondaryLabel)
            SfSymbol("chevron.down", colors.tertiaryLabel, KeeprTheme.typography.caption.semibold)
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            KeeprMenu(
                expanded = expanded,
                onDismiss = { expanded = false },
                entries = options.map { option ->
                    MenuEntry.Action(label(option), symbol?.invoke(option), isChecked = option == selection) { onSelect(option) }
                },
            )
        }
    }
}

/** A toggle row: `Toggle("Favorite", isOn:)` in a form. */
@Composable
fun FormToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ListRow(modifier, verticalPadding = 10.dp) {
        KText(title, Modifier.weight(1f))
        KeeprToggle(checked, onCheckedChange)
    }
}

/**
 * `DatePicker` in its compact form style: the date (and time) in gray capsules
 * that open the system's calendar and clock. Material's dialogs do the picking,
 * recolored to Keepr's accent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDateRow(
    title: String,
    date: Instant,
    onDateChange: (Instant) -> Unit,
    modifier: Modifier = Modifier,
    showsTime: Boolean = false,
    latest: Instant? = null,
    calendar: KeeprCalendar = KeeprCalendar.current(),
) {
    val colors = KeeprTheme.colors
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    ListRow(modifier, verticalPadding = 8.dp) {
        KText(title, Modifier.weight(1f))
        Capsule(calendar.monthDayYear(date)) { pickingDate = true }
        if (showsTime) Capsule(calendar.shortTime(date)) { pickingTime = true }
    }

    if (pickingDate) {
        val local = calendar.localDate(date)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = local.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val limit = latest ?: return true
                    val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !day.isAfter(calendar.localDate(limit))
                }
            },
        )
        val pickerColors = DatePickerDefaults.colors(
            containerColor = colors.secondaryGroupedBackground,
            selectedDayContainerColor = colors.accent,
            todayDateBorderColor = colors.accent,
            todayContentColor = colors.accent,
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                KeeprButton("Done", {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateChange(withDay(date, picked, calendar))
                    }
                    pickingDate = false
                }, modifier = Modifier.padding(16.dp), textStyle = KeeprTheme.typography.headline)
            },
            colors = pickerColors,
        ) {
            DatePicker(state = state, colors = pickerColors, showModeToggle = false)
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(calendar.hour(date), calendar.minute(date))
        Dialog(onDismissRequest = { pickingTime = false }) {
            Column(
                Modifier
                    .background(colors.secondaryGroupedBackground, RoundedCornerShape(Radius.menu))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        selectorColor = colors.accent,
                        timeSelectorSelectedContainerColor = colors.accent.copy(alpha = 0.2f),
                    ),
                )
                KeeprButton("Done", {
                    onDateChange(calendar.settingTime(state.hour, state.minute, 0, date))
                    pickingTime = false
                }, textStyle = KeeprTheme.typography.headline)
            }
        }
    }
}

private fun withDay(original: Instant, day: LocalDate, calendar: KeeprCalendar): Instant {
    val time = calendar.zoned(original).toLocalTime()
    return day.atTime(time).atZone(calendar.zone).toInstant()
}

@Composable
private fun Capsule(text: String, onClick: () -> Unit) {
    KText(
        text,
        modifier = Modifier
            .background(KeeprTheme.colors.tertiaryFill, RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

/** A row of choices as capsule buttons, the selected one tinted — the follow-up date presets. */
@Composable
fun PresetChips(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = KeeprTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ListMetrics.rowHorizontal, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, (title, selected) ->
            val tint = if (selected) colors.accent else colors.secondaryLabel
            KText(
                title,
                style = KeeprTheme.typography.subheadline,
                color = tint,
                modifier = Modifier
                    .background(tint.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}
