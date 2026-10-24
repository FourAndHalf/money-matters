package com.moneymatters.app.feature.dashboard

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.feature.accounts.AccountStack
import com.moneymatters.app.feature.accounts.AccountSummary
import com.moneymatters.app.feature.transactions.SummaryRow
import com.moneymatters.app.feature.transactions.TransactionSummary
import com.moneymatters.app.ui.components.AccountRow
import com.moneymatters.app.ui.components.CircleIconButton
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.NetWorthCard
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme


/** Simple Home (plan.html → P03, Phase 1): balances and the open period's totals, straight from the ledger. */
@Immutable
data class HomeState(
    val dateLabel: String,
    val netWorth: Paise,
    val assetsTotal: Paise,
    val liabilitiesTotal: Paise,
    /** e.g. "Since 1 Oct": the open period runs from the profile's month start day. */
    val periodLabel: String,
    val income: Paise,
    val spent: Paise,
    val accounts: List<AccountSummary>,
    val recent: List<TransactionSummary>,
    val draftCount: Int,
)

/**
 * Home for Phase 1. The full dashboard ([DashboardScreen]: leeway, runway, budgets, goals) replaces
 * it in Phase 3, once those numbers exist in the ledger.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onOpenAccounts: () -> Unit,
    onOpenAccount: (accountId: String) -> Unit,
    onEditCard: (AccountSummary) -> Unit = {},
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = status.calculateTopPadding() + contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
    ) {
        padded(margin, "header") {
            Row(
                Modifier.fillMaxWidth().padding(start = 2.dp, top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Home", style = MoneyTheme.type.headlineXlMobile, color = MoneyTheme.colors.ink)
                    Text(state.dateLabel, style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 3.dp))
                }
                CircleIconButton(MoneyIcons.Settings, "Settings", onOpenSettings, size = 40.dp, iconSize = 18.dp)
            }
        }
        padded(margin, "net-worth") { NetWorthCard(state.netWorth, state.assetsTotal, state.liabilitiesTotal) }
        padded(margin, "month") {
            Module {
                ModuleHeader("Month so far", subtitle = state.periodLabel)
                KeyValueRow("Income", state.income.formatInr(signed = true))
                KeyValueRow("Spent", (-state.spent).formatInr(signed = true))
                KeyValueRow("Net", (state.income - state.spent).formatInr(signed = true), divider = false)
            }
        }
        if (state.draftCount > 0) {
            padded(margin, "drafts") {
                Module {
                    ListRow(
                        title = "${state.draftCount} ${if (state.draftCount == 1) "draft" else "drafts"} waiting",
                        subtitle = "Drafts don't count in any balance until you post them",
                        divider = false,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onOpenTransactions),
                        trailing = { StatusBadge("Draft", Tone.Info) },
                    )
                }
            }
        }
        padded(margin, "accounts") {
            Column {
                ModuleHeader("Accounts", subtitle = if (state.accounts.size > 1) "Swipe the stack" else null, modifier = Modifier.padding(start = 2.dp, top = 8.dp), action = { Pill("View all", onOpenAccounts) })
                if (state.accounts.isEmpty()) Module { EmptyLine("No accounts yet.") } else AccountStack(state.accounts, onOpenAccount, onEditCard = onEditCard)
            }
        }
        padded(margin, "recent") {
            Module {
                ModuleHeader("Recent", action = { Pill("View all", onOpenTransactions) })
                state.recent.forEachIndexed { i, txn -> SummaryRow(txn, onOpenTransaction, divider = i < state.recent.lastIndex) }
                if (state.recent.isEmpty()) EmptyLine("No transactions yet. Tap + to add one.")
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted, modifier = Modifier.padding(vertical = 12.dp))
}

private fun LazyListScope.padded(margin: androidx.compose.ui.unit.Dp, key: String, content: @Composable () -> Unit) {
    item(key) { Column(Modifier.padding(horizontal = margin)) { content() } }
}
