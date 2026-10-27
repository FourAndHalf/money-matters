package com.moneymatters.app.feature.accounts

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.AccountRow
import com.moneymatters.app.ui.components.CollapsibleBody
import com.moneymatters.app.ui.components.CollapsibleModuleHeader
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.NetWorthCard
import com.moneymatters.app.ui.components.ProgressTrack
import com.moneymatters.app.ui.components.RowDivider
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import com.moneymatters.app.ui.theme.MoneyTheme

/** Accounts (P08): the account tree grouped by type, a net worth summary and earmarks. */
@Composable
fun AccountsScreen(
    state: AccountsState,
    onOpenAccount: (accountId: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onEditCard: (AccountSummary) -> Unit = {},
    onTogglePin: (AccountSummary) -> Unit = {},
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = status.calculateTopPadding() + contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
    ) {
        item("header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = margin, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Accounts", style = MoneyTheme.type.headlineXlMobile, color = MoneyTheme.colors.ink)
            }
        }
        item("net-worth") {
            NetWorthCard(state.netWorth, state.assetsTotal, state.liabilitiesTotal, Modifier.padding(horizontal = margin))
        }
        val stack = state.groups.flatMap { it.items }
        if (stack.isNotEmpty()) {
            item("stack") {
                AccountStack(
                    stack, onOpenAccount, Modifier.padding(horizontal = margin), onEditCard,
                    // The group of the account up front opens, and any other one closes.
                    onShown = { shown -> openGroup = state.groups.firstOrNull { g -> g.items.any { it.id == shown.id } }?.title },
                    onTogglePin = onTogglePin,
                )
            }
        }
        state.groups.forEach { group ->
            item(group.title) {
                Column(Modifier.padding(horizontal = margin)) {
                    AccountGroupModule(
                        group, onOpenAccount,
                        expanded = openGroup == group.title,
                        onToggle = { openGroup = if (openGroup == group.title) null else group.title },
                    )
                }
            }
        }
        if (state.earmarks.isNotEmpty()) {
            item("earmarks") {
                Column(Modifier.padding(horizontal = margin)) {
                    EarmarksModule(state.earmarks)
                }
            }
        }
    }
}

@Composable
private fun AccountGroupModule(group: AccountGroup, onOpenAccount: (String) -> Unit, expanded: Boolean, onToggle: () -> Unit) {
    Module {
        CollapsibleModuleHeader(
            group.title, expanded, onToggle,
            subtitle = "${group.items.size} ${if (group.items.size == 1) "account" else "accounts"}",
        )
        CollapsibleBody(expanded) {
            group.items.forEachIndexed { i, account ->
                AccountRow(
                    name = account.name,
                    subtitle = account.subtitle,
                    kind = account.kind,
                    balance = account.balance,
                    isLiability = account.isLiability,
                    onClick = { onOpenAccount(account.id) },
                    divider = i < group.items.lastIndex,
                    badge = account.badge?.let { b -> { StatusBadge(b.text, b.tone) } },
                )
            }
        }
    }
}

@Composable
private fun EarmarksModule(earmarks: List<Earmark>) {
    Module {
        ModuleHeader("Earmarks", subtitle = "Set aside inside your liquid balance")
        earmarks.forEachIndexed { i, e ->
            Column(Modifier.padding(vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(e.name, style = MoneyTheme.type.ledgerMono, color = MoneyTheme.colors.ink)
                    Text("${e.saved.formatInr()} / ${e.target.formatInr()}", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, softWrap = false)
                }
                ProgressTrack(fraction = e.saved.value.toFloat() / e.target.value)
            }
            if (i < earmarks.lastIndex) RowDivider()
        }
    }
}

@Preview(widthDp = 392, heightDp = 1800, showBackground = true, backgroundColor = 0xFFE8E8EA)
@Composable
private fun AccountsScreenPreview() {
    MoneyMattersTheme { AccountsScreen(SampleAccounts.state, onOpenAccount = {}) }
}
