package com.moneymatters.app.feature.accounts

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.AmountWithBadge
import com.moneymatters.app.ui.components.BarSegment
import com.moneymatters.app.ui.components.GlyphChip
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.MoneyText
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.ProgressTrack
import com.moneymatters.app.ui.components.RowDivider
import com.moneymatters.app.ui.components.SegmentBar
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import com.moneymatters.app.ui.theme.MoneyTheme

/** Accounts (P08): the account tree grouped by type, a net worth summary and earmarks. */
@Composable
fun AccountsScreen(
    state: AccountsState,
    onOpenAccount: (accountId: String) -> Unit,
    modifier: Modifier = Modifier,
    onAddAccount: () -> Unit = {},
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
        item("header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = margin, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Accounts", style = MoneyTheme.type.headlineXlMobile, color = MoneyTheme.colors.ink)
                Pill("+ Add", onAddAccount, style = PillStyle.Primary)
            }
        }
        item("net-worth") {
            NetWorthHero(state, Modifier.padding(horizontal = margin))
        }
        state.groups.forEach { group ->
            item(group.title) {
                Column(Modifier.padding(horizontal = margin)) {
                    AccountGroupModule(group, onOpenAccount)
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
private fun NetWorthHero(state: AccountsState, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    val total = (state.assetsTotal.value + state.liabilitiesTotal.value).coerceAtLeast(1)
    Module(modifier, shape = MoneyTheme.shapes.xl) {
        Text("Net worth", style = MoneyTheme.type.bodySm, color = c.muted)
        MoneyText(state.netWorth, style = MoneyTheme.type.metricHero, showPaise = false, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        SegmentBar(
            segments = listOf(
                BarSegment(state.assetsTotal.value.toFloat() / total, c.anchor),
                BarSegment(state.liabilitiesTotal.value.toFloat() / total, c.negative),
            ),
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Assets", style = MoneyTheme.type.bodySm, color = c.muted)
                Text(state.assetsTotal.formatInr(), style = MoneyTheme.type.metricTable, color = c.ink)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Liabilities", style = MoneyTheme.type.bodySm, color = c.muted)
                Text(state.liabilitiesTotal.formatInr(), style = MoneyTheme.type.metricTable, color = c.negative, textAlign = TextAlign.End)
            }
        }
    }
}

private fun AccountKind.icon() = when (this) {
    AccountKind.Bank -> MoneyIcons.Bank
    AccountKind.Cash -> null
    AccountKind.CreditCard -> MoneyIcons.Accounts
    AccountKind.Loan -> MoneyIcons.Percent
    AccountKind.FixedDeposit -> null
    AccountKind.Investment -> MoneyIcons.Invest
}

private fun AccountKind.glyph() = when (this) {
    AccountKind.Cash -> "₹"
    AccountKind.FixedDeposit -> "◈"
    else -> null
}

@Composable
private fun AccountGroupModule(group: AccountGroup, onOpenAccount: (String) -> Unit) {
    Module {
        ModuleHeader(group.title, subtitle = "${group.items.size} ${if (group.items.size == 1) "account" else "accounts"}")
        group.items.forEachIndexed { i, account ->
            val amountText = if (account.isLiability) "−${account.balance.formatInr()}" else account.balance.formatInr()
            ListRow(
                title = account.name,
                subtitle = account.subtitle,
                divider = i < group.items.lastIndex,
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = "Open ${account.name} ledger",
                    onClick = { onOpenAccount(account.id) },
                ),
                leading = { GlyphChip(icon = account.kind.icon(), text = account.kind.glyph()) },
                trailing = {
                    AmountWithBadge(amountText) {
                        account.badge?.let { StatusBadge(it.text, it.tone) }
                    }
                },
            )
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
                    Text("${e.saved.formatInr()} / ${e.target.formatInr()}", style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted)
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
