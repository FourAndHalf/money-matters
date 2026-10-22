package com.moneymatters.app.feature.invest

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import com.moneymatters.app.core.invest.RestateKind
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.SegmentedControl
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Record a dividend: what reached the account, and any tax withheld. */
@Composable
fun DividendScreen(viewModel: InvestViewModel, holdingName: String, symbol: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val form = viewModel.dividendForm ?: return
    val accounts by viewModel.settlementAccounts.collectAsState()
    DividendContent(
        holdingName, form, accounts, viewModel.error,
        onChange = { viewModel.dividendForm = it },
        onSave = { viewModel.saveDividend(symbol, onSaved = onClose) },
        onClose = onClose,
        modifier = modifier,
    )
}

@Composable
internal fun DividendContent(
    holdingName: String,
    form: DividendForm,
    accounts: List<AccountEntity>,
    error: String?,
    onChange: (DividendForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dateText by remember { mutableStateOf(form.date.toString()) }
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Dividend", subtitle = holdingName, onBack = onClose) }
        Column(Modifier.weight(1f).padding(horizontal = margin, vertical = MoneyTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter)) {
            Module {
                FormField(
                    "Date", dateText,
                    { text ->
                        dateText = text
                        try { onChange(form.copy(date = LocalDate.parse(text))) } catch (_: DateTimeParseException) { /* keep typing */ }
                    },
                    placeholder = "yyyy-MM-dd",
                )
                Column(Modifier.padding(top = 10.dp)) { FormField("Received in your account (₹)", form.netText, { onChange(form.copy(netText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 1,800") }
                Column(Modifier.padding(top = 10.dp)) { FormField("Tax deducted at source (₹, optional)", form.tdsText, { onChange(form.copy(tdsText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 200") }
                Text("Tax withheld is kept as a receivable to claim when you file.", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp))
            }
            Module {
                Text("Received into", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a -> Pill(a.name, { onChange(form.copy(accountId = a.id)) }, style = if (form.accountId == a.id) PillStyle.Primary else PillStyle.Secondary) }
                }
            }
            if (error != null) Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative)
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill("Save dividend", { if (form.canSave) onSave() }, style = PillStyle.Primary, height = 44.dp, modifier = Modifier.fillMaxWidth().alpha(if (form.canSave) 1f else 0.4f))
        }
    }
}

/** Record a split or bonus; it restates the lots without moving any money. */
@Composable
fun ActionScreen(viewModel: InvestViewModel, holdingName: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val form = viewModel.actionForm ?: return
    ActionContent(holdingName, form, viewModel.error, onChange = { viewModel.actionForm = it }, onSave = { viewModel.saveAction(onSaved = onClose) }, onClose = onClose, modifier = modifier)
}

@Composable
internal fun ActionContent(
    holdingName: String,
    form: ActionForm,
    error: String?,
    onChange: (ActionForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dateText by remember { mutableStateOf(form.date.toString()) }
    val isSplit = form.kind == RestateKind.Split
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Split or bonus", subtitle = holdingName, onBack = onClose) }
        Column(Modifier.weight(1f).padding(horizontal = margin, vertical = MoneyTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter)) {
            Module {
                SegmentedControl(RestateKind.entries, form.kind, { onChange(form.copy(kind = it)) }, { it.name })
                Column(Modifier.padding(top = 12.dp)) {
                    FormField(
                        "Effective date", dateText,
                        { text ->
                            dateText = text
                            try { onChange(form.copy(date = LocalDate.parse(text))) } catch (_: DateTimeParseException) { /* keep typing */ }
                        },
                        placeholder = "yyyy-MM-dd",
                    )
                }
                Column(Modifier.padding(top = 10.dp)) {
                    FormField(
                        if (isSplit) "New units for each unit held" else "Bonus units for each unit held",
                        form.ratioText, { onChange(form.copy(ratioText = it)) }, keyboardType = KeyboardType.Decimal,
                        placeholder = if (isSplit) "e.g. 5 for a 1:5 split" else "e.g. 1 for 1:1, 0.5 for 1:2",
                    )
                }
                Text(
                    if (isSplit) "Each lot keeps its cost and purchase date; its units are multiplied."
                    else "Bonus shares arrive at zero cost with a holding period that starts on this date.",
                    style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (error != null) Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative)
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill("Save", { if (form.canSave) onSave() }, style = PillStyle.Primary, height = 44.dp, modifier = Modifier.fillMaxWidth().alpha(if (form.canSave) 1f else 0.4f))
        }
    }
}

/** Give an order that was waiting In Transit its units. */
@Composable
fun AllotScreen(viewModel: InvestViewModel, order: PendingRow, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val form = viewModel.allotForm ?: return
    AllotContent(order, form, viewModel.error, { viewModel.allotForm = it }, { viewModel.saveAllot(onSaved = onClose) }, onClose, modifier)
}

@Composable
internal fun AllotContent(
    order: PendingRow,
    form: AllotForm,
    error: String?,
    onChange: (AllotForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dateText by remember { mutableStateOf(form.date.toString()) }
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Allot units", subtitle = "${order.holdingName} · ${order.amount.formatInr()}", onBack = onClose) }
        Column(Modifier.weight(1f).padding(horizontal = margin, vertical = MoneyTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter)) {
            Module {
                FormField(
                    "Allotment date", dateText,
                    { text ->
                        dateText = text
                        try { onChange(form.copy(date = LocalDate.parse(text))) } catch (_: DateTimeParseException) { /* keep typing */ }
                    },
                    placeholder = "yyyy-MM-dd",
                )
                Column(Modifier.padding(top = 10.dp)) { FormField("Units allotted", form.unitsText, { onChange(form.copy(unitsText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 123.456") }
                Column(Modifier.padding(top = 10.dp)) { FormField("NAV (optional)", form.navText, { onChange(form.copy(navText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "worked out from the amount if blank") }
            }
            if (error != null) Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative)
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill("Allot units", { if (form.canSave) onSave() }, style = PillStyle.Primary, height = 44.dp, modifier = Modifier.fillMaxWidth().alpha(if (form.canSave) 1f else 0.4f))
        }
    }
}
