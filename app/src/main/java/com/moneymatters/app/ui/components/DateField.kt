package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Shown = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * A date input that opens a calendar instead of asking for typed digits; looks like [FormField].
 * The picker works in UTC midnights, so the day you tap is the day you get, in any time zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val c = MoneyTheme.colors
    var open by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MoneyTheme.type.labelRegular, color = if (error != null) c.negative else c.muted)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(MoneyTheme.shapes.md)
                .background(c.surfaceLowest)
                .border(1.dp, if (error != null) c.negative else c.outline, MoneyTheme.shapes.md)
                .clickable(role = Role.Button, onClickLabel = "Pick $label") { open = true }
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(date?.format(Shown) ?: "Pick a date", style = MoneyTheme.type.bodyLg, color = if (date == null) c.muted else c.ink)
            Icon(MoneyIcons.ChevronDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp).semantics { contentDescription = "Calendar" })
        }
        if (error != null) Text(error, style = MoneyTheme.type.bodySm, color = c.negative, modifier = Modifier.padding(top = 4.dp))
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
