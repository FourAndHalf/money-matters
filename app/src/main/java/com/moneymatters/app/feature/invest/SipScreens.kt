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
import com.moneymatters.app.core.invest.InstalmentState
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.DateField
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.CalendarLegend
import com.moneymatters.app.ui.components.ConfirmDialog
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.MonthCalendar
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate

/** Start a monthly SIP into a fund you already hold. */
@Composable
fun SipFormScreen(viewModel: InvestViewModel, funds: List<HoldingRow>, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val form = viewModel.sipForm ?: return
    val accounts by viewModel.settlementAccounts.collectAsState()
    SipFormContent(form, funds, accounts, viewModel.error, { viewModel.sipForm = it }, { viewModel.saveSip(onSaved = onClose) }, onClose, modifier)
}

@Composable
internal fun SipFormContent(
    form: SipForm,
    funds: List<HoldingRow>,
    accounts: List<AccountEntity>,
    error: String?,
    onChange: (SipForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = if (form.editingPlanId != null) "Edit SIP" else "New SIP", onBack = onClose) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = margin, vertical = MoneyTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            if (form.editingPlanId == null) item("fund") {
                Module {
                    Text("Fund", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
                    if (funds.isEmpty()) {
                        Text("Buy a mutual fund first, then set up its SIP.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            funds.forEach { f -> Pill(f.name, { onChange(form.copy(holdingId = f.id)) }, style = if (form.holdingId == f.id) PillStyle.Primary else PillStyle.Secondary) }
                        }
                    }
                }
            }
            item("fields") {
                Module {
                    FormField("Amount each month (₹)", form.amountText, { onChange(form.copy(amountText = it)) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 10,000")
                    Column(Modifier.padding(top = 10.dp)) { FormField("Day of the month", form.dayText, { onChange(form.copy(dayText = it)) }, keyboardType = KeyboardType.Number, placeholder = "1 to 31", error = if (form.day == null) "Use a day from 1 to 31" else null) }
                    if (form.editingPlanId == null) Column(Modifier.padding(top = 10.dp)) {
                        DateField("First instalment on or after", form.start, { onChange(form.copy(start = it)) })
                    }
                }
            }
            item("account") {
                Module {
                    Text("Paid from", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
                    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.forEach { a -> Pill(a.name, { onChange(form.copy(accountId = a.id)) }, style = if (form.accountId == a.id) PillStyle.Primary else PillStyle.Secondary) }
                    }
                    Text("Each month you confirm the instalment here; the money then waits In Transit until the units are allotted.", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 10.dp))
                }
            }
            if (error != null) item("error") { Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) }
        }
        Column(Modifier.padding(horizontal = margin, vertical = 12.dp)) {
            Pill(if (form.editingPlanId != null) "Save changes" else "Start SIP", { if (form.canSave) onSave() }, style = PillStyle.Primary, height = 44.dp, modifier = Modifier.fillMaxWidth().alpha(if (form.canSave) 1f else 0.4f))
        }
    }
}

/** One SIP: its schedule, the instalments waiting for you, what missing them costs the goal, and how to stop it. */
@Composable
fun SipDetailScreen(
    detail: SipDetail,
    onBack: () -> Unit,
    onPay: (DueRow) -> Unit,
    onSkip: (DueRow) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    /** Why the last edit or delete was refused, if it was. */
    error: String? = null,
) {
    val row = detail.row
    var confirmStop by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) { DetailTopBar(title = row.fundName, subtitle = "${row.amount.formatInr()} ${row.scheduleLabel}", onBack = onBack) }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            if (error != null) item("error") { Module { Text(error, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.negative) } }
            item("facts") {
                Module {
                    KeyValueRow("Paid from", detail.fromAccountName)
                    KeyValueRow("Instalments paid", detail.paidCount.toString())
                    KeyValueRow("Next instalment", if (row.ended) "Ended" else row.nextLabel ?: "—", divider = false)
                }
            }
            detail.calendar?.let { cal -> item("calendar") { Module { SipCalendarBody(cal) } } }
            if (detail.missedImpact != null && detail.goalLabel != null) {
                item("impact") {
                    Module {
                        ModuleHeader("Missed instalments cost your goal", subtitle = "They would have grown to about ${detail.missedImpact.formatInr()} by ${detail.goalLabel}.")
                    }
                }
            }
            if (detail.dues.isNotEmpty()) {
                item("dues") {
                    Module {
                        ModuleHeader("Waiting for you", subtitle = "Confirm an instalment once you've paid it")
                        detail.dues.forEachIndexed { i, due ->
                            ListRow(
                                title = due.dateLabel,
                                subtitle = row.amount.formatInr(),
                                divider = i < detail.dues.lastIndex,
                                leading = {
                                    when (due.state) {
                                        InstalmentState.Missed -> StatusBadge("Missed", Tone.Negative)
                                        InstalmentState.Skipped -> StatusBadge("Skipped", Tone.Neutral)
                                        else -> StatusBadge("Due", Tone.Warning)
                                    }
                                },
                                trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (due.state != InstalmentState.Skipped) Pill("Skip", { onSkip(due) }, height = 28.dp)
                                        Pill("Paid", { onPay(due) }, style = PillStyle.Primary, height = 28.dp)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item("manage") {
                Module {
                    ModuleHeader("Manage", subtitle = "Change the amount or day, or remove the SIP. Instalments already paid stay.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!row.ended) Pill("Edit SIP", onEdit)
                        Pill("Delete SIP", { confirmDelete = true }, style = PillStyle.Destructive)
                    }
                }
            }
            if (!row.ended) {
                // Stopping can't be undone (a new SIP would have to be set up), so it takes two taps.
                item("stop") {
                    Pill(
                        if (confirmStop) "Tap again to stop this SIP" else "Stop this SIP",
                        { if (confirmStop) onStop() else confirmStop = true },
                        style = PillStyle.Destructive, modifier = Modifier.fillMaxWidth(), height = 40.dp,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Delete this SIP?",
            "The plan and its skipped instalments are removed. Orders already placed stay as ordinary orders, and no further instalments are proposed.",
            "Delete", onDelete, { confirmDelete = false },
        )
    }
}

/** The month grid with its key, opening on the current month (or the nearest one the SIP has). */
@Composable
internal fun SipCalendarBody(calendar: SipCalendar, modifier: Modifier = Modifier) {
    var month by remember(calendar) { mutableStateOf(java.time.YearMonth.from(calendar.today).coerceIn(calendar.first, calendar.last)) }
    Column(modifier) {
        MonthCalendar(
            month = month, marks = calendar.marks, today = calendar.today,
            canPrev = month > calendar.first, canNext = month < calendar.last,
            onPrev = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) },
        )
        CalendarLegend(calendar.marks.values.toSet(), Modifier.padding(top = 10.dp))
    }
}
