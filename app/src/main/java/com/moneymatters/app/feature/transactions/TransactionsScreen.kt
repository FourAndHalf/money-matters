package com.moneymatters.app.feature.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.ActivityCalendar
import com.moneymatters.app.ui.components.GlyphChip
import com.moneymatters.app.ui.components.InnerPanel
import com.moneymatters.app.ui.components.PostingLineRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.RowDivider
import com.moneymatters.app.ui.components.SearchField
import com.moneymatters.app.ui.components.SegmentedControl
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.components.TransactionRow
import com.moneymatters.app.ui.components.amountText
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class JournalMode { Everyday, Accountant }

/**
 * Transactions (P06): a daily list with search, status and account filters, a suspense inbox
 * for uncategorized entries, and an accountant-mode toggle that shows raw Dr/Cr postings.
 * A view selector switches between the daily list, a calendar of daily totals, and weekly and monthly totals.
 */
@Composable
fun TransactionsScreen(
    state: TransactionsState,
    onOpenTransaction: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    today: LocalDate = LocalDate.now(),
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    var view by remember { mutableStateOf(TxnView.List) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<TxnStatus?>(null) }
    var accountFilter by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(JournalMode.Everyday) }

    val visibleGroups = remember(state, query, statusFilter, accountFilter) {
        state.filtered(TransactionFilters(query, statusFilter, accountFilter))
    }
    val latestMonth = remember(visibleGroups, today) { maxOf(YearMonth.from(today), visibleGroups.mapNotNull { it.date }.maxOrNull()?.let(YearMonth::from) ?: YearMonth.from(today)) }
    var month by remember { mutableStateOf(YearMonth.from(visibleGroups.firstOrNull()?.date ?: today)) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = status.calculateTopPadding() + contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
    ) {
        item("header") {
            Text(
                "Transactions", style = MoneyTheme.type.headlineXlMobile, color = MoneyTheme.colors.ink,
                modifier = Modifier.padding(horizontal = margin, vertical = 10.dp),
            )
        }
        item("views") {
            SegmentedControl(
                options = TxnView.entries, selectedOption = view, onSelect = { view = it }, label = { it.label },
                modifier = Modifier.padding(horizontal = margin),
            )
        }
        item("search") {
            Column(Modifier.padding(horizontal = margin), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(query, { query = it })
                StatusFilterRow(statusFilter) { statusFilter = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccountFilterPill(state.accounts, accountFilter) { accountFilter = it }
                    SegmentedControl(
                        options = listOf(JournalMode.Everyday, JournalMode.Accountant),
                        selectedOption = mode,
                        onSelect = { mode = it },
                        label = { if (it == JournalMode.Everyday) "Everyday" else "Accountant" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (state.uncategorizedCount > 0) {
            item("suspense") {
                Column(Modifier.padding(horizontal = margin)) {
                    SuspenseModule(state, onOpenTransaction)
                }
            }
        }
        if (visibleGroups.isEmpty()) {
            item("empty") {
                Text(
                    if (state.groups.isEmpty()) "No transactions yet. Tap + to add one." else "No transactions match.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted,
                    modifier = Modifier.padding(horizontal = margin, vertical = 24.dp),
                )
            }
        }
        when (view) {
            TxnView.List -> visibleGroups.forEach { group ->
                item(group.dateLabel) {
                    Column(Modifier.padding(horizontal = margin)) {
                        DayModule(group, mode, onOpenTransaction)
                    }
                }
            }
            TxnView.Calendar -> {
                item("calendar") {
                    Column(Modifier.padding(horizontal = margin)) {
                        Module {
                            ActivityCalendar(
                                month, dayNets(visibleGroups), selectedDay, today,
                                canPrev = visibleGroups.any { g -> g.date?.let { YearMonth.from(it) < month } == true },
                                canNext = month < latestMonth,
                                onPrev = { month = month.minusMonths(1); selectedDay = null },
                                onNext = { month = month.plusMonths(1); selectedDay = null },
                                onSelect = { selectedDay = it },
                            )
                        }
                    }
                }
                item("day") {
                    Column(Modifier.padding(horizontal = margin)) {
                        val group = selectedDay?.let { d -> visibleGroups.firstOrNull { it.date == d } }
                        when {
                            group != null -> DayModule(group, mode, onOpenTransaction)
                            selectedDay != null -> Text("Nothing on ${selectedDay!!.format(DayMonthLong)}.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted)
                            else -> Text("Tap a day to see its entries.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted)
                        }
                    }
                }
            }
            TxnView.Weekly -> item("weekly") {
                Column(Modifier.padding(horizontal = margin)) { PeriodsModule(weeklyRows(visibleGroups), "Weeks") { r -> "${r.start.format(DayMonthShort)} – ${r.end.format(DayMonthShort)}" } }
            }
            TxnView.Monthly -> item("monthly") {
                Column(Modifier.padding(horizontal = margin)) { PeriodsModule(monthlyRows(visibleGroups), "Months") { r -> r.start.format(MonthYear) } }
            }
        }
    }
}

/** Where the Transactions tab looks at your entries from: a daily list, a calendar of daily totals, or weekly and monthly totals. */
internal enum class TxnView(val label: String) { List("List"), Calendar("Calendar"), Weekly("Weekly"), Monthly("Monthly") }

private val DayMonthShort = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DayMonthLong = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
private val MonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

@Composable
private fun PeriodsModule(rows: List<PeriodRow>, title: String, label: (PeriodRow) -> String) {
    val c = MoneyTheme.colors
    val total = totalFlow(rows)
    Module {
        ModuleHeader(title, subtitle = "Out ${total.out.formatInr()} · In ${total.into.formatInr()} · Net ${total.net.formatInr(signed = true)}")
        rows.forEachIndexed { i, r ->
            ListRow(
                title = label(r),
                subtitle = "${r.flow.count} ${if (r.flow.count == 1) "entry" else "entries"} · Net ${r.flow.net.formatInr(signed = true)}",
                divider = i < rows.lastIndex,
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Out ${r.flow.out.formatInr()}", style = MoneyTheme.type.bodySm, color = c.negative)
                        Text("In ${r.flow.into.formatInr()}", style = MoneyTheme.type.bodySm, color = c.positive)
                    }
                },
            )
        }
    }
}

@Composable
private fun StatusFilterRow(selected: TxnStatus?, onSelect: (TxnStatus?) -> Unit) {
    val options: List<Pair<String, TxnStatus?>> = listOf(
        "All" to null,
        "Draft" to TxnStatus.Draft,
        "Posted" to TxnStatus.Posted,
        "Reconciled" to TxnStatus.Reconciled,
        "Locked" to TxnStatus.Locked,
        "Prior-period" to TxnStatus.PriorPeriodAdjustment,
    )
    // Six pills are wider than a phone, so the row scrolls sideways rather than squeezing or cutting the last ones.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            Pill(
                label, { onSelect(value) },
                style = if (value == selected) PillStyle.Primary else PillStyle.Secondary,
                modifier = Modifier.testTag("status-filter-$label"),
            )
        }
    }
}

@Composable
private fun AccountFilterPill(accounts: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Pill(selected ?: "All accounts", { expanded = true }, height = 40.dp, trailingIcon = MoneyIcons.ChevronDown)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All accounts") }, onClick = { onSelect(null); expanded = false })
            accounts.forEach { account ->
                DropdownMenuItem(text = { Text(account) }, onClick = { onSelect(account); expanded = false })
            }
        }
    }
}

@Composable
private fun SuspenseModule(state: TransactionsState, onOpenTransaction: (String) -> Unit) {
    val items = state.groups.flatMap { it.items }.filter { it.uncategorized }
    Module {
        ModuleHeader("Suspense inbox", subtitle = "${items.size} to categorize", action = { Pill("Categorize all", {}) })
        items.forEachIndexed { i, txn ->
            ListRow(
                title = txn.payee,
                subtitle = "${txn.accountName} · ${amountText(txn.amount)}",
                divider = i < items.lastIndex,
                leading = { GlyphChip(text = txn.chip) },
                trailing = { Pill("Categorize", { onOpenTransaction(txn.id) }) },
            )
        }
    }
}

@Composable
private fun DayModule(group: DayGroup, mode: JournalMode, onOpenTransaction: (String) -> Unit) {
    Module {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(group.dateLabel, style = MoneyTheme.type.headlineMd, color = MoneyTheme.colors.ink)
            Text(group.total.formatInr(signed = true), style = MoneyTheme.type.metricTable, color = MoneyTheme.colors.ink, softWrap = false)
        }
        when (mode) {
            JournalMode.Everyday -> group.items.forEachIndexed { i, txn ->
                SummaryRow(txn, onOpenTransaction, divider = i < group.items.lastIndex)
            }
            JournalMode.Accountant -> AccountantPanel(group)
        }
    }
}

/** One transaction as an everyday row; shared with Home's recent list. */
@Composable
internal fun SummaryRow(txn: TransactionSummary, onOpenTransaction: (String) -> Unit, divider: Boolean = true) {
    TransactionRow(
        payee = txn.payee,
        subtitle = buildString {
            append(txn.category); append(" · "); append(txn.accountName)
            txn.decision?.let { append(" · "); append(it.name.lowercase().replaceFirstChar(Char::uppercase)) }
        },
        chip = txn.chip,
        amount = txn.amount,
        isTransfer = txn.isTransfer,
        onClick = { onOpenTransaction(txn.id) },
        divider = divider,
        badge = txn.status.look()?.let { (text, tone, icon) -> { StatusBadge(text, tone, icon = icon) } },
    )
}

/** DESIGN.md → Application notes: journal views use ledger-mono in an inner panel, Dr/Cr columns, a Balanced badge. */
@Composable
private fun AccountantPanel(group: DayGroup) {
    val c = MoneyTheme.colors
    InnerPanel {
        group.items.forEachIndexed { i, txn ->
            txn.postings.forEach { posting ->
                PostingLineRow(posting.account, posting.debit, posting.credit, Modifier.padding(vertical = 4.dp))
            }
            if (i < group.items.lastIndex) RowDivider(Modifier.padding(vertical = 4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            StatusBadge("✓ Balanced", Tone.Positive)
        }
    }
}

@Preview(widthDp = 392, heightDp = 1600, showBackground = true, backgroundColor = 0xFFE8E8EA)
@Composable
private fun TransactionsScreenPreview() {
    MoneyMattersTheme { TransactionsScreen(SampleTransactions.state, onOpenTransaction = {}) }
}
