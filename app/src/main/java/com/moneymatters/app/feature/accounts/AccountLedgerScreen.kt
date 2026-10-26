package com.moneymatters.app.feature.accounts

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.GlyphChip
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.MoneyText
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.StatusBadge
import com.moneymatters.app.ui.components.Tone
import com.moneymatters.app.ui.components.amountColor
import com.moneymatters.app.ui.components.amountText
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyMattersTheme
import com.moneymatters.app.ui.theme.MoneyTheme

/**
 * Account ledger (P09): entries with a running balance, reconciled/unreconciled markers and,
 * for credit cards, statement-cycle grouping. The period picker is cosmetic until the real
 * ledger exists — sample data doesn't yet vary by period.
 */
@Composable
fun AccountLedgerScreen(
    state: AccountLedgerState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onReconcile: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Box(Modifier.padding(top = status.calculateTopPadding() + contentPadding.calculateTopPadding())) {
            DetailTopBar(
                title = state.accountName,
                subtitle = state.accountTypeLabel,
                onBack = onBack,
                action = { Pill("Reconcile", onReconcile, style = PillStyle.Primary) },
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("balance") {
                Column(Modifier.padding(horizontal = margin)) {
                    BalanceHero(state)
                }
            }
            state.sections.forEachIndexed { index, section ->
                item(section.title ?: "section-$index") {
                    Column(Modifier.padding(horizontal = margin)) {
                        SectionModule(section, state.periodLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHero(state: AccountLedgerState) {
    val c = MoneyTheme.colors
    var periodExpanded by remember(state.accountId) { mutableStateOf(false) }
    var selectedPeriod by rememberSaveable(state.accountId) { mutableStateOf(state.periodLabel) }
    val displayAmount = if (state.isLiability) -state.currentBalance else state.currentBalance

    Module(shape = MoneyTheme.shapes.xl) {
        Text(if (state.isLiability) "Outstanding" else "Current balance", style = MoneyTheme.type.bodySm, color = c.muted)
        MoneyText(displayAmount, style = MoneyTheme.type.metricHero, showPaise = false, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box {
                Pill(selectedPeriod, { periodExpanded = true }, trailingIcon = MoneyIcons.ChevronDown)
                DropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                    state.availablePeriods.forEach { period ->
                        DropdownMenuItem(text = { Text(period) }, onClick = { selectedPeriod = period; periodExpanded = false })
                    }
                }
            }
            if (state.unreconciledCount > 0) {
                StatusBadge("${state.unreconciledCount} unreconciled", Tone.Warning)
            } else {
                StatusBadge("✓ All reconciled", Tone.Positive)
            }
        }
    }
}

@Composable
private fun SectionModule(section: LedgerSection, periodLabel: String) {
    Module {
        if (section.title != null) {
            ModuleHeader(section.title, subtitle = section.subtotal?.let { "Charges ${it.formatInr()}" })
        } else {
            ModuleHeader("Transactions", subtitle = periodLabel)
        }
        section.entries.forEachIndexed { i, entry ->
            ListRow(
                title = entry.payee,
                subtitle = "${entry.dateLabel} · ${entry.meta}",
                divider = i < section.entries.lastIndex,
                leading = { GlyphChip(text = entry.chip) },
                trailing = { EntryTrailing(entry) },
            )
        }
    }
}

@Composable
private fun EntryTrailing(entry: LedgerEntry) {
    val c = MoneyTheme.colors
    Column(horizontalAlignment = Alignment.End) {
        Text(amountText(entry.amount), style = MoneyTheme.type.metricTable, color = amountColor(entry.amount, entry.isTransfer), textAlign = TextAlign.End, softWrap = false)
        entry.status?.let { status ->
            val (text, tone, icon) = status.badge()
            StatusBadge(text, tone, icon = icon, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            "Bal ${entry.runningBalance.formatInr()}",
            style = MoneyTheme.type.bodySm.copy(fontSize = MoneyTheme.type.metricDelta.fontSize),
            color = c.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private data class StatusLook(val text: String, val tone: Tone, val icon: androidx.compose.ui.graphics.vector.ImageVector?)

private fun EntryStatus.badge(): StatusLook = when (this) {
    EntryStatus.Draft -> StatusLook("Draft", Tone.Info, null)
    EntryStatus.Reconciled -> StatusLook("Reconciled", Tone.Neutral, MoneyIcons.Lock)
    EntryStatus.PriorPeriod -> StatusLook("Prior-period", Tone.Warning, null)
}

@Preview(widthDp = 392, heightDp = 1400, showBackground = true, backgroundColor = 0xFFE8E8EA)
@Composable
private fun AccountLedgerScreenPreview() {
    MoneyMattersTheme { AccountLedgerScreen(SampleLedgers.byAccountId.getValue("icici-credit"), onBack = {}) }
}
