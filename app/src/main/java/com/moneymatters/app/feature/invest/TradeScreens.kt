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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.invest.FundClass
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.TradeSide
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.money.impliedPrice
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.SegmentedControl
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Buy or sell a stock (P04's investment types, now with units and lots). Holding accounts never appear as the money side. */
@Composable
fun TradeScreen(viewModel: InvestViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val accounts by viewModel.settlementAccounts.collectAsState()
    TradeContent(
        form = viewModel.tradeForm,
        accounts = accounts,
        error = viewModel.error,
        isSaving = viewModel.isSaving,
        onChange = { viewModel.tradeForm = it },
        onSave = { viewModel.saveTrade(onSaved = onClose) },
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
internal fun TradeContent(
    form: TradeForm,
    accounts: List<AccountEntity>,
    error: String?,
    isSaving: Boolean,
    onChange: (TradeForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, replaces the stock/fund toggle and the title: the Add investment screen picks the kind itself. */
    header: (@Composable () -> Unit)? = null,
) {
    val isBuy = form.side == TradeSide.Buy
    var dateText by remember { mutableStateOf(form.date.toString()) }
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    val canSave = form.canSave && !isSaving
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = if (header != null) "Add investment" else (if (isBuy) "Buy " else "Sell ") + (if (form.isFund) "fund" else "stock"), onBack = onClose)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("fields") {
                Module {
                    if (header != null) Column(Modifier.padding(bottom = 12.dp)) { header() }
                    if (form.holdingId == null) {
                        if (header == null) SegmentedControl(Instrument.entries, form.instrument, { onChange(form.copy(instrument = it)) }, { if (it == Instrument.Stock) "Stock" else "Mutual fund" })
                        Column(Modifier.padding(top = 12.dp)) {
                            if (!form.isFund) {
                                FormField("Symbol", form.symbol, { onChange(form.copy(symbol = it)) }, placeholder = "e.g. INFY")
                                Column(Modifier.padding(top = 10.dp)) { FormField("Company (optional)", form.name, { onChange(form.copy(name = it)) }, placeholder = "e.g. Infosys") }
                            } else {
                                FormField("Fund name", form.name, { onChange(form.copy(name = it)) }, placeholder = "e.g. Parag Parikh Flexi Cap")
                            }
                            Column(Modifier.padding(top = 10.dp)) { FormField("ISIN (optional)", form.isin, { onChange(form.copy(isin = it)) }, placeholder = "e.g. INE009A01021") }
                            if (form.isFund) {
                                Text("Type", style = MoneyTheme.type.labelRegular, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FundClass.entries.forEach { c -> Pill(c.label(), { onChange(form.copy(fundClass = c)) }, style = if (form.fundClass == c) PillStyle.Primary else PillStyle.Secondary) }
                                }
                                Column(Modifier.padding(top = 10.dp)) { FormField("Exit load window in days (optional)", form.exitLoadDaysText, { onChange(form.copy(exitLoadDaysText = it)) }, keyboardType = KeyboardType.Number, placeholder = "e.g. 365") }
                            }
                        }
                    }
                    Column(Modifier.padding(top = if (form.holdingId == null) 10.dp else 0.dp)) {
                        FormField(
                            "Date", dateText,
                            { text ->
                                dateText = text
                                try { onChange(form.copy(date = LocalDate.parse(text))) } catch (_: DateTimeParseException) { /* keep typing */ }
                            },
                            placeholder = "yyyy-MM-dd",
                            error = if (runCatching { LocalDate.parse(dateText) }.isFailure) "Use yyyy-MM-dd" else null,
                        )
                    }
                    Column(Modifier.padding(top = 10.dp)) {
                        FormField(
                            if (form.isFund && isBuy) "Units allotted (blank if not yet)" else "Units",
                            form.unitsText, { onChange(form.copy(unitsText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 12.5",
                        )
                    }
                    Column(Modifier.padding(top = 10.dp)) {
                        FormField(
                            if (isBuy) "Total paid (₹, after all charges)" else "Total received (₹, after all charges)",
                            form.amountText, { onChange(form.copy(amountText = it)) },
                            keyboardType = KeyboardType.Decimal, placeholder = "e.g. 15,432.10",
                        )
                    }
                    val units = form.units
                    val amount = form.amount
                    if (form.inTransit) {
                        Text("The money moves to In Transit until the units are allotted.", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp))
                    } else if (units != null && amount != null) {
                        Text(
                            "≈ ${formatUnitPrice(impliedPrice(units, amount).tenThousandths)} per unit",
                            style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            item("account") {
                Module {
                    Text(if (isBuy) "Paid from" else "Received into", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
                    if (accounts.isEmpty()) {
                        Text("Add a bank or cash account first.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            accounts.forEach { account ->
                                Pill(account.name, { onChange(form.copy(accountId = account.id)) }, style = if (form.accountId == account.id) PillStyle.Primary else PillStyle.Secondary)
                            }
                        }
                    }
                }
            }
            if (error != null) {
                item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) }
            }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(
                if (isSaving) "Saving…" else if (form.inTransit) "Send to In Transit" else if (isBuy) "Save buy" else "Save sale",
                onClick = { if (canSave) onSave() },
                style = PillStyle.Primary,
                height = 44.dp,
                modifier = Modifier.fillMaxWidth().alpha(if (canSave) 1f else 0.4f),
            )
        }
    }
}

/** Enter a price by hand for a holding; it is recorded as Manual and shown with that badge. */
@Composable
fun PriceScreen(viewModel: InvestViewModel, holdingName: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val form = viewModel.priceForm ?: return
    PriceContent(
        holdingName = holdingName,
        form = form,
        error = viewModel.error,
        onChange = { viewModel.priceForm = it },
        onSave = { viewModel.savePrice(onSaved = onClose) },
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
internal fun PriceContent(
    holdingName: String,
    form: PriceForm,
    error: String?,
    onChange: (PriceForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dateText by remember { mutableStateOf(form.date.toString()) }
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = "Update price", subtitle = holdingName, onBack = onClose)
        }
        Column(Modifier.weight(1f).padding(horizontal = margin, vertical = MoneyTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter)) {
            Module {
                FormField(
                    "Price date", dateText,
                    { text ->
                        dateText = text
                        try { onChange(form.copy(date = LocalDate.parse(text))) } catch (_: DateTimeParseException) { /* keep typing */ }
                    },
                    placeholder = "yyyy-MM-dd",
                )
                Column(Modifier.padding(top = 10.dp)) {
                    FormField("Price per unit (₹)", form.priceText, { onChange(form.copy(priceText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 1,512.35")
                }
                Text("Saved as a manual price. Downloaded prices will replace it with a verified one.", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp))
            }
            if (error != null) Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative)
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(
                "Save price", onClick = { if (form.canSave) onSave() }, style = PillStyle.Primary, height = 44.dp,
                modifier = Modifier.fillMaxWidth().alpha(if (form.canSave) 1f else 0.4f),
            )
        }
    }
}
