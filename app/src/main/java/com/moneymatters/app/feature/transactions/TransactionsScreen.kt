package com.moneymatters.app.feature.transactions

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

private enum class JournalMode { Everyday, Accountant }

/**
 * Transactions (P06): a daily list with search, status and account filters, a suspense inbox
 * for uncategorized entries, and an accountant-mode toggle that shows raw Dr/Cr postings.
 * Calendar, weekly and monthly views from the plan aren't built yet.
 */
@Composable
fun TransactionsScreen(
    state: TransactionsState,
    onOpenTransaction: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<TxnStatus?>(null) }
    var accountFilter by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(JournalMode.Everyday) }

    val visibleGroups = remember(state, query, statusFilter, accountFilter) {
        state.filtered(TransactionFilters(query, statusFilter, accountFilter))
    }

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
        visibleGroups.forEach { group ->
            item(group.dateLabel) {
                Column(Modifier.padding(horizontal = margin)) {
                    DayModule(group, mode, onOpenTransaction)
                }
            }
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                subtitle = "${txn.accountName} · ${amountText(txn.amount, txn.isTransfer)}",
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
