package com.moneymatters.app.feature.accounts

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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.parseRupees
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.theme.MoneyTheme

/** A card's limit (in rupees) and statement day being changed, as typed. */
@Immutable
data class CardEditForm(val accountId: String, val name: String, val limitText: String = "", val dayText: String = "") {
    val limit: Paise? get() = parseRupees(limitText)?.takeIf { it.value > 0 }
    val day: Int? get() = dayText.trim().toIntOrNull()?.takeIf { it in 1..28 }
    val canSave: Boolean get() = limit != null && day != null
}

/** Change a credit card's limit and statement day; works for a card added without them too. */
@Composable
fun EditCardScreen(
    form: CardEditForm,
    error: String?,
    isSaving: Boolean,
    onChange: (CardEditForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    val canSave = form.canSave && !isSaving
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Edit card", subtitle = form.name, onBack = onClose) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("fields") {
                Module {
                    FormField(
                        "Credit limit (₹)", form.limitText, { onChange(form.copy(limitText = it)) },
                        placeholder = "e.g. 1,00,000", keyboardType = KeyboardType.Decimal, testTag = "card-limit-field",
                        error = if (form.limitText.isNotBlank() && form.limit == null) "Enter an amount above zero" else null,
                    )
                    Column(Modifier.padding(top = 10.dp)) {
                        FormField(
                            "Statement day of the month", form.dayText, { onChange(form.copy(dayText = it.filter(Char::isDigit).take(2))) },
                            placeholder = "1 to 28", keyboardType = KeyboardType.Number, testTag = "card-statement-field",
                            error = if (form.dayText.isNotEmpty() && form.day == null) "Pick a day from 1 to 28" else null,
                        )
                    }
                    Text("Only the limit and statement day change. What you owe stays as the entries say.", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 12.dp))
                }
            }
            if (error != null) item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(if (isSaving) "Saving…" else "Save changes", { if (canSave) onSave() }, style = PillStyle.Primary, height = 44.dp, modifier = Modifier.fillMaxWidth().alpha(if (canSave) 1f else 0.4f))
        }
    }
}
