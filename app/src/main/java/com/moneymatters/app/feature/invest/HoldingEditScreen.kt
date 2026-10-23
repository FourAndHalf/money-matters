package com.moneymatters.app.feature.invest

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.invest.FundClass
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.ui.components.DateField
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.theme.MoneyTheme

/** Correct what a holding is called and how it is classified. Units, lots and the ledger stay as they are. */
@Composable
fun HoldingEditScreen(viewModel: InvestViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val form = viewModel.holdingEditForm ?: return
    HoldingEditContent(form, viewModel.error, viewModel.isSaving, { viewModel.holdingEditForm = it }, { viewModel.saveHoldingEdit(onSaved = onClose) }, onClose, modifier)
}

@Composable
internal fun HoldingEditContent(
    form: HoldingEditForm,
    error: String?,
    isSaving: Boolean,
    onChange: (HoldingEditForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    val canSave = form.canSave && !isSaving
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Edit details", onBack = onClose) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("fields") {
                Module {
                    when (form.instrument) {
                        Instrument.Stock -> {
                            FormField("Symbol", form.symbol, { onChange(form.copy(symbol = it)) }, placeholder = "e.g. INFY")
                            Column(Modifier.padding(top = 10.dp)) { FormField("Company (optional)", form.name, { onChange(form.copy(name = it)) }, placeholder = "e.g. Infosys") }
                        }
                        Instrument.MutualFund -> FormField("Fund name", form.name, { onChange(form.copy(name = it)) })
                        Instrument.Bond -> FormField("Bond name", form.name, { onChange(form.copy(name = it)) })
                    }
                    Column(Modifier.padding(top = 10.dp)) { FormField("ISIN (optional)", form.isin, { onChange(form.copy(isin = it)) }, placeholder = "e.g. INE009A01021") }
                    if (form.instrument == Instrument.MutualFund) {
                        Text("Type", style = MoneyTheme.type.labelRegular, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FundClass.entries.forEach { c -> Pill(c.label(), { onChange(form.copy(fundClass = c)) }, style = if (form.fundClass == c) PillStyle.Primary else PillStyle.Secondary) }
                        }
                        Column(Modifier.padding(top = 10.dp)) {
                            FormField("Exit load window in days (optional)", form.exitLoadDaysText, { onChange(form.copy(exitLoadDaysText = it)) }, keyboardType = KeyboardType.Number)
                        }
                    }
                    if (form.instrument == Instrument.Bond) {
                        Column(Modifier.padding(top = 10.dp)) {
                            FormField("Coupon (% a year, optional)", form.couponText, { onChange(form.copy(couponText = it)) }, keyboardType = KeyboardType.Decimal, error = if (form.couponText.isNotBlank() && form.couponBps == null) "Use a percentage like 7.5" else null)
                        }
                        Column(Modifier.padding(top = 10.dp)) { DateField("Maturity date (optional)", form.maturity, { onChange(form.copy(maturity = it)) }) }
                    }
                    Text(
                        "Your units, cost and past entries don't change. Renaming also renames the account they sit in.",
                        style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            if (error != null) item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(if (isSaving) "Saving…" else "Save changes", { if (canSave) onSave() }, style = PillStyle.Primary, height = 44.dp, modifier = Modifier.fillMaxWidth().alpha(if (canSave) 1f else 0.4f))
        }
    }
}
