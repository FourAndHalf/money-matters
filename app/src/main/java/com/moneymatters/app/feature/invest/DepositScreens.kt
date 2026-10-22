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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.invest.Compounding
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.MoneyText
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.SegmentedControl
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Toggle
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate
import java.time.format.DateTimeParseException

private fun dateField(onValid: (LocalDate) -> Unit): (String) -> Unit = { text ->
    try { onValid(LocalDate.parse(text)) } catch (_: DateTimeParseException) { /* keep typing */ }
}

/** Open a fixed deposit, new or already running. */
@Composable
fun DepositScreen(viewModel: InvestViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val form = viewModel.depositForm ?: return
    val accounts by viewModel.settlementAccounts.collectAsState()
    DepositContent(form, accounts, viewModel.error, viewModel.isSaving, { viewModel.depositForm = it }, { viewModel.saveDeposit(onSaved = onClose) }, onClose, modifier)
}

@Composable
internal fun DepositContent(
    form: DepositForm,
    accounts: List<AccountEntity>,
    error: String?,
    isSaving: Boolean,
    onChange: (DepositForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var startText by remember { mutableStateOf(form.start.toString()) }
    var maturityText by remember { mutableStateOf(form.maturity.toString()) }
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    val canSave = form.canSave && !isSaving
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "New fixed deposit", onBack = onClose) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("fields") {
                Module {
                    FormField("Name", form.name, { onChange(form.copy(name = it)) }, placeholder = "e.g. HDFC FD 1")
                    Column(Modifier.padding(top = 10.dp)) { FormField("Principal (₹)", form.principalText, { onChange(form.copy(principalText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 1,00,000") }
                    Column(Modifier.padding(top = 10.dp)) { FormField("Interest rate (% a year)", form.rateText, { onChange(form.copy(rateText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 7.25") }
                    Text("Interest paid", style = MoneyTheme.type.labelRegular, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    SegmentedControl(Compounding.entries, form.compounding, { onChange(form.copy(compounding = it)) }, { if (it == Compounding.Simple) "At maturity" else it.name })
                    Column(Modifier.padding(top = 10.dp)) {
                        FormField(
                            "Start date", startText,
                            { startText = it; dateField { d -> onChange(form.copy(start = d)) }(it) },
                            placeholder = "yyyy-MM-dd",
                        )
                    }
                    Column(Modifier.padding(top = 10.dp)) {
                        FormField(
                            "Maturity date", maturityText,
                            { maturityText = it; dateField { d -> onChange(form.copy(maturity = d)) }(it) },
                            placeholder = "yyyy-MM-dd",
                            error = if (!form.datesValid) "Must be after the start date" else null,
                        )
                    }
                    Column(Modifier.padding(top = 10.dp)) { FormField("Early-break penalty (% off the rate)", form.penaltyText, { onChange(form.copy(penaltyText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 1") }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Renew automatically at maturity", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.ink)
                        Toggle(form.autoRenew, { onChange(form.copy(autoRenew = it)) })
                    }
                }
            }
            item("funding") {
                Module {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Already running (don't move money)", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.ink, modifier = Modifier.weight(1f))
                        Toggle(form.existing, { onChange(form.copy(existing = it)) })
                    }
                    if (!form.existing) {
                        Text("Paid from", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 12.dp))
                        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            accounts.forEach { a -> Pill(a.name, { onChange(form.copy(accountId = a.id)) }, style = if (form.accountId == a.id) PillStyle.Primary else PillStyle.Secondary) }
                        }
                    } else {
                        Text("Recorded against your opening balances, so no bank balance changes. Interest earned so far is counted from the start date.", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (error != null) item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(if (isSaving) "Saving…" else "Save deposit", { if (canSave) onSave() }, style = PillStyle.Primary, height = 44.dp, modifier = Modifier.fillMaxWidth().alpha(if (canSave) 1f else 0.4f))
        }
    }
}

/** A deposit's value, terms, what it pays at maturity or if broken today, and its actions. */
@Composable
fun DepositDetailScreen(
    detail: DepositDetail,
    onBack: () -> Unit,
    onPostInterest: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = detail.row
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = row.name, subtitle = "${detail.rateLabel} · ${detail.compoundingLabel}", onBack = onBack) }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("summary") {
                Module(shape = MoneyTheme.shapes.xl) {
                    Text(if (detail.isClosed) "Paid out" else "Value today", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted)
                    MoneyText(row.value, style = MoneyTheme.type.metricHero, showPaise = false, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                    StatusBadge(row.badge.text, row.badge.tone, height = 24.dp)
                    if (!detail.isClosed) {
                        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (detail.unpostedInterest.value > 0) Pill("Post interest", onPostInterest)
                            Pill(if (detail.matured) "Mature" else "Break early", onClose, style = PillStyle.Primary)
                        }
                    }
                }
            }
            item("facts") {
                Module {
                    KeyValueRow("Principal", row.principal.formatInr(showPaise = true))
                    KeyValueRow("Interest earned so far", detail.accruedInterest.formatInr(showPaise = true))
                    if (!detail.isClosed) {
                        KeyValueRow("…of which not yet posted", detail.unpostedInterest.formatInr(showPaise = true))
                        KeyValueRow("Value at maturity", detail.maturityValue.formatInr(showPaise = true))
                    }
                    KeyValueRow("Started", detail.startLabel)
                    KeyValueRow("Matures", detail.maturityLabel)
                    if (detail.autoRenew) KeyValueRow("Renews automatically", "Yes")
                    if (detail.isClosed) KeyValueRow("Closed", detail.closedLabel.orEmpty(), divider = false)
                    else KeyValueRow("If broken today", detail.breakProceeds.formatInr(showPaise = true), divider = false)
                }
            }
            if (!detail.isClosed && detail.breakPenalty.value > 0) {
                item("penalty") {
                    Module { ModuleHeader("Breaking early costs ${detail.breakPenalty.formatInr(showPaise = true)}", subtitle = "Interest is recalculated at the lower rate. There's no penalty within 30 days of maturity.") }
                }
            }
        }
    }
}

/** Pay a deposit out: on maturity, or early at the penalty rate. */
@Composable
fun DepositCloseScreen(viewModel: InvestViewModel, detail: DepositDetail, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val form = viewModel.closeForm ?: return
    val accounts by viewModel.settlementAccounts.collectAsState()
    DepositCloseContent(detail, form, accounts, viewModel.error, viewModel.isSaving, { viewModel.closeForm = it }, { viewModel.saveClose(onSaved = onClose) }, onClose, modifier)
}

@Composable
internal fun DepositCloseContent(
    detail: DepositDetail,
    form: DepositCloseForm,
    accounts: List<AccountEntity>,
    error: String?,
    isSaving: Boolean,
    onChange: (DepositCloseForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dateText by remember { mutableStateOf(form.date.toString()) }
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    val canSave = form.canSave && !isSaving
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = if (detail.matured) "Mature deposit" else "Break deposit", subtitle = detail.row.name, onBack = onClose)
        }
        Column(Modifier.weight(1f).padding(horizontal = margin, vertical = MoneyTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter)) {
            Module {
                FormField("Date", dateText, { dateText = it; dateField { d -> onChange(form.copy(date = d)) }(it) }, placeholder = "yyyy-MM-dd")
                Column(Modifier.padding(top = 10.dp)) { FormField("Tax deducted at source (₹, optional)", form.tdsText, { onChange(form.copy(tdsText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 1,200") }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Renew into a new deposit", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.ink)
                    Toggle(form.renew, { onChange(form.copy(renew = it)) })
                }
            }
            if (!form.renew) {
                Module {
                    Text("Paid into", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
                    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.forEach { a -> Pill(a.name, { onChange(form.copy(toAccountId = a.id)) }, style = if (form.toAccountId == a.id) PillStyle.Primary else PillStyle.Secondary) }
                    }
                }
            }
            if (error != null) Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative)
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(
                if (isSaving) "Saving…" else if (detail.matured) "Mature" else "Break deposit",
                { if (canSave) onSave() }, style = if (detail.matured) PillStyle.Primary else PillStyle.Destructive, height = 44.dp,
                modifier = Modifier.fillMaxWidth().alpha(if (canSave) 1f else 0.4f),
            )
        }
    }
}
