package com.moneymatters.app.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.theme.MoneyTheme

/** Edit a Draft or Posted entry: its date, payee, note and (for a plain two-account entry) amount. */
@Composable
fun EditEntryScreen(
    form: EditEntryForm,
    error: String?,
    isSaving: Boolean,
    onChange: (EditEntryForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = MoneyTheme.spacing.margin
    val c = MoneyTheme.colors
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Edit entry", onBack = onClose) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("fields") {
                Module {
                    FormField("Payee", form.payee, { onChange(form.copy(payee = it)) })
                    Column(Modifier.padding(top = 10.dp)) {
                        FormField(
                            "Date", form.dateText, { onChange(form.copy(dateText = it)) },
                            placeholder = "yyyy-MM-dd",
                            error = if (form.date == null) "Use the form yyyy-MM-dd" else null,
                        )
                    }
                    if (form.amountEditable) {
                        Column(Modifier.padding(top = 10.dp)) {
                            FormField(
                                "Amount (₹)", form.amountText, { onChange(form.copy(amountText = it)) },
                                keyboardType = KeyboardType.Decimal,
                                error = if (form.amount == null) "Enter an amount above zero" else null,
                            )
                        }
                    }
                    Column(Modifier.padding(top = 10.dp)) { FormField("Note", form.note, { onChange(form.copy(note = it)) }) }
                }
            }
            item("legs") {
                Module {
                    Text("Accounts", style = MoneyTheme.type.labelUppercase, color = c.muted)
                    form.legs.forEach { Text(it, style = MoneyTheme.type.ledgerMono, color = c.ink, modifier = Modifier.padding(top = 8.dp)) }
                    Text(
                        if (form.amountEditable) "Changing the amount moves both sides together, so the entry stays balanced."
                        else "The amounts and accounts of this entry can't be changed here. To change them, reverse it and enter it again.",
                        style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            if (error != null) item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = c.negative) }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(
                if (isSaving) "Saving…" else "Save changes", { if (form.canSave && !isSaving) onSave() },
                style = PillStyle.Primary, height = 48.dp,
                modifier = Modifier.fillMaxWidth().alpha(if (form.canSave) 1f else 0.4f),
            )
        }
    }
}
