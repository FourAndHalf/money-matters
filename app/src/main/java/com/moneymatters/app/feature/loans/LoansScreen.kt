package com.moneymatters.app.feature.loans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.feature.recurring.toPlainRupees
import com.moneymatters.app.ui.components.DateField
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.ProgressTrack
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Day = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

internal fun rateLabel(bps: Int) = "${bps / 100}.${(bps % 100).toString().padStart(2, '0')}%"

/** Loans: the list, one loan's schedule and payments, and the form for its terms. */
@Composable
fun LoansScreen(viewModel: LoansViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val undescribed by viewModel.undescribed.collectAsState()
    val payFrom by viewModel.payFromAccounts.collectAsState()
    val form = viewModel.form
    val detail = viewModel.openAccountId?.let { state.details[it] }
    when {
        form != null -> {
            BackHandler { viewModel.cancelForm() }
            LoanFormContent(form, if (form.editingAccountId != null) emptyList() else undescribed, payFrom, viewModel.error, { viewModel.form = it }, viewModel::pickAccount, viewModel::save, viewModel::cancelForm, modifier)
        }
        detail != null -> {
            BackHandler { viewModel.back() }
            LoanDetailContent(
                detail, viewModel.paidOn, viewModel.prepayText, viewModel.error,
                onPaidOn = { viewModel.paidOn = it }, onPrepayText = { viewModel.prepayText = it },
                onPayEmi = viewModel::payEmi, onPrepay = viewModel::prepay, onEdit = { viewModel.startEdit(detail.row.accountId) },
                onRemove = viewModel::deleteTerms, onBack = viewModel::back, modifier = modifier,
            )
        }
        else -> {
            BackHandler(onBack = onBack)
            LoansListContent(state, onNew = viewModel::startNew, onOpen = viewModel::open, onBack = onBack, modifier = modifier)
        }
    }
}

@Composable
internal fun LoansListContent(state: LoansState, onNew: () -> Unit, onOpen: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = "Loans", onBack = onBack) }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            if (state.rows.isNotEmpty()) item("summary") {
                Module {
                    Text("Owed in total", style = MoneyTheme.type.labelUppercase, color = c.muted)
                    Text(state.totalOutstanding.formatInr(), style = MoneyTheme.type.bodyLg, color = c.ink, modifier = Modifier.padding(top = 4.dp))
                    Text("EMIs each month: ${state.totalEmi.formatInr()}", style = MoneyTheme.type.bodySm, color = c.muted)
                }
            }
            item("loans") {
                Module {
                    ModuleHeader("Your loans", subtitle = "${state.rows.size} with EMI details", action = { Pill("+ Add", onNew, height = 28.dp) })
                    if (state.rows.isEmpty()) Text(
                        "Add a loan account under Accounts first, then describe its rate and EMI here to see its schedule and interest.",
                        style = MoneyTheme.type.bodyMd, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                    )
                    state.rows.forEachIndexed { i, r ->
                        ListRow(
                            title = r.name,
                            subtitle = when {
                                r.isCleared -> "Paid off"
                                else -> "${rateLabel(r.rateBps)} · EMI ${r.emi.formatInr()} · next ${r.nextDue?.format(Day)}"
                            },
                            divider = i < state.rows.lastIndex,
                            modifier = Modifier.clickable { onOpen(r.accountId) },
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(r.outstanding.formatInr(), style = MoneyTheme.type.bodyMd, color = c.ink)
                                    if (r.isOverdue) StatusBadge("EMI overdue", Tone.Negative)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LoanDetailContent(
    detail: LoanDetail,
    paidOn: LocalDate,
    prepayText: String,
    error: String?,
    onPaidOn: (LocalDate) -> Unit,
    onPrepayText: (String) -> Unit,
    onPayEmi: () -> Unit,
    onPrepay: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MoneyTheme.colors
    val r = detail.row
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = r.name, subtitle = "${rateLabel(r.rateBps)} a year", onBack = onBack) }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("summary") {
                Module {
                    Text(if (r.isCleared) "Paid off" else "Owed", style = MoneyTheme.type.labelUppercase, color = c.muted)
                    Text(r.outstanding.formatInr(showPaise = true), style = MoneyTheme.type.metricHero, color = c.ink, modifier = Modifier.padding(top = 4.dp))
                    val fraction = if (r.original.value > 0) (r.principalRepaid.value.toFloat() / r.original.value).coerceIn(0f, 1f) else 0f
                    ProgressTrack(fraction, Modifier.padding(top = 10.dp))
                    Text("${r.principalRepaid.formatInr()} of ${r.original.formatInr()} repaid", style = MoneyTheme.type.bodySm, color = c.muted)
                    KeyValueRow("EMI", r.emi.formatInr(showPaise = true), modifier = Modifier.padding(top = 8.dp))
                    KeyValueRow("Interest paid so far", r.interestPaid.formatInr(showPaise = true))
                    if (!r.isCleared) {
                        KeyValueRow("Instalments left", "${r.instalmentsLeft}")
                        KeyValueRow("Interest still to pay", r.interestLeft.formatInr(showPaise = true))
                        KeyValueRow("Last instalment", r.lastDue?.format(Day) ?: "Never at this EMI", divider = false)
                    }
                }
            }
            if (!r.isCleared) item("pay") {
                Module {
                    ModuleHeader("Pay", subtitle = "From ${r.payFromName}")
                    if (r.isOverdue) StatusBadge("EMI overdue since ${r.nextDue?.format(Day)}", Tone.Negative)
                    Column(Modifier.padding(top = 10.dp)) { DateField("Paid on", paidOn, onPaidOn) }
                    Text(
                        "Next EMI due ${r.nextDue?.format(Day)}: ${r.nextSplitPrincipal.formatInr(showPaise = true)} principal + ${r.nextSplitInterest.formatInr(showPaise = true)} interest.",
                        style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 8.dp),
                    )
                    Pill("Pay EMI of ${(r.nextSplitPrincipal + r.nextSplitInterest).formatInr(showPaise = true)}", onPayEmi, style = PillStyle.Primary, height = 40.dp, modifier = Modifier.padding(top = 10.dp).fillMaxWidth())
                    Column(Modifier.padding(top = 14.dp)) {
                        FormField("Prepay (₹)", prepayText, onPrepayText, keyboardType = KeyboardType.Decimal, placeholder = "Reduces what you owe, with no interest")
                    }
                    Pill("Prepay", { if (prepayText.isNotBlank()) onPrepay() }, modifier = Modifier.padding(top = 10.dp).alpha(if (prepayText.isNotBlank()) 1f else 0.4f))
                    if (error != null) Text(error, style = MoneyTheme.type.bodyMd, color = c.negative, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (detail.schedule.isNotEmpty()) item("schedule") {
                Module {
                    ModuleHeader("Schedule", subtitle = "${detail.schedule.size} instalments")
                    detail.schedule.take(12).forEachIndexed { i, s ->
                        ListRow(
                            title = s.due.format(Day), subtitle = "Principal ${s.principal.formatInr(showPaise = true)} · Interest ${s.interest.formatInr(showPaise = true)}",
                            divider = i < minOf(12, detail.schedule.size) - 1,
                            trailing = { Column(horizontalAlignment = Alignment.End) { Text(s.paid.formatInr(showPaise = true), style = MoneyTheme.type.bodyMd, color = c.ink); Text("Left ${s.balanceAfter.formatInr()}", style = MoneyTheme.type.bodySm, color = c.muted) } },
                        )
                    }
                    if (detail.schedule.size > 12) Text("${detail.schedule.size - 12} more instalments after these.", style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (detail.history.isNotEmpty()) item("history") {
                Module {
                    ModuleHeader("Paid through the app")
                    detail.history.forEachIndexed { i, h ->
                        ListRow(
                            title = h.date.format(Day) + if (h.isPrepayment) " · prepayment" else "",
                            subtitle = if (h.isPrepayment) "Principal ${h.principal.formatInr(showPaise = true)}" else "Principal ${h.principal.formatInr(showPaise = true)} · Interest ${h.interest.formatInr(showPaise = true)}",
                            divider = i < detail.history.lastIndex,
                        )
                    }
                }
            }
            item("manage") {
                Module {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Edit terms", onEdit)
                        Pill("Remove EMI details", onRemove, style = PillStyle.Destructive)
                    }
                    Text("Removing the details keeps the account and every entry it made.", style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
internal fun LoanFormContent(
    form: LoanForm,
    loanAccounts: List<AccountEntity>,
    payFrom: List<AccountEntity>,
    error: String?,
    onChange: (LoanForm) -> Unit,
    onPickAccount: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MoneyTheme.colors
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = if (form.editingAccountId != null) "Edit loan terms" else "Add loan details", onBack = onClose) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            if (form.editingAccountId == null) item("account") {
                Module {
                    Text("Loan account", style = MoneyTheme.type.labelUppercase, color = c.muted)
                    if (loanAccounts.isEmpty()) Text("Add a loan account under Accounts first.", style = MoneyTheme.type.bodyMd, color = c.muted, modifier = Modifier.padding(top = 8.dp))
                    else Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        loanAccounts.forEach { a -> Pill(a.name, { onPickAccount(a.id) }, style = if (form.accountId == a.id) PillStyle.Primary else PillStyle.Secondary) }
                    }
                }
            }
            item("terms") {
                Module {
                    FormField("Amount borrowed (₹)", form.originalText, { onChange(form.copy(originalText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 5,00,000")
                    Column(Modifier.padding(top = 10.dp)) { FormField("Interest a year (%)", form.rateText, { onChange(form.copy(rateText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 8.5", error = if (form.rateText.isNotBlank() && form.rateBps == null) "Use a rate like 8.5" else null) }
                    Column(Modifier.padding(top = 10.dp)) { FormField("EMI (₹)", form.emiText, { onChange(form.copy(emiText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "As your bank charges it") }
                    Column(Modifier.padding(top = 10.dp)) { FormField("Not sure of the EMI? Tenure in months", form.tenureText, { onChange(form.copy(tenureText = it)) }, keyboardType = KeyboardType.Number, placeholder = "e.g. 60") }
                    form.suggestedEmi?.let { s ->
                        Pill("Use ${s.formatInr()} a month", { onChange(form.copy(emiText = s.toPlainRupees())) }, modifier = Modifier.padding(top = 8.dp))
                        Text("Worked out from the amount, rate and tenure. Your bank's EMI may differ by a few rupees.", style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(Modifier.padding(top = 10.dp)) { DateField("Next EMI due on", form.firstDue, { onChange(form.copy(firstDue = it)) }) }
                }
            }
            item("payfrom") {
                Module {
                    Text("Paid from", style = MoneyTheme.type.labelUppercase, color = c.muted)
                    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        payFrom.forEach { a -> Pill(a.name, { onChange(form.copy(payFromId = a.id)) }, style = if (form.payFromId == a.id) PillStyle.Primary else PillStyle.Secondary) }
                    }
                    Text("What you owe always comes from your books. These details give the schedule and split each EMI into principal and interest.", style = MoneyTheme.type.bodySm, color = c.muted, modifier = Modifier.padding(top = 10.dp))
                }
            }
            if (error != null) item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = c.negative) }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(if (form.editingAccountId != null) "Save changes" else "Save loan details", { if (form.canSave) onSave() }, style = PillStyle.Primary, height = 44.dp, modifier = Modifier.fillMaxWidth().alpha(if (form.canSave) 1f else 0.4f))
        }
    }
}
