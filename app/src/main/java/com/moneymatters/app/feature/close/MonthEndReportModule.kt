package com.moneymatters.app.feature.close

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.close.MonthEndReport
import com.moneymatters.app.core.close.formatBps
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.theme.MoneyTheme

private fun Paise.inr() = formatInr(showPaise = true)

/** The month-end report as a preview before closing (plan.html → P17): where the month stands, against last month and the usual. */
@Composable
fun MonthEndReportModule(r: MonthEndReport, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    Module(modifier) {
        ModuleHeader("Month-end report", subtitle = "${r.period.label} preview")
        KeyValueRow("Income", r.income.inr())
        KeyValueRow("Expenses", r.expenses.inr())
        KeyValueRow(if (r.surplus.value >= 0) "Surplus" else "Deficit", r.surplus.inr())
        KeyValueRow("Savings rate", r.savingsRateBps?.let(::formatBps) ?: "No income this month")
        KeyValueRow("Net invested", r.netInvested.inr())
        KeyValueRow("Investment rate", r.investmentRateBps?.let(::formatBps) ?: "No income this month")
        Text("Investing is not spending, so the two rates are reported separately.", style = MoneyTheme.type.bodySm, color = c.muted)

        Section("Net worth")
        KeyValueRow("Start of month", r.netWorthStart.inr())
        KeyValueRow("End of month", r.netWorthEnd.inr())
        KeyValueRow("Change", (r.netWorthEnd - r.netWorthStart).formatInr(showPaise = true, signed = true), divider = false)

        Section("Spending compared")
        KeyValueRow("Last month", r.previousExpenses.inr())
        KeyValueRow("Average of the three before", if (r.averageExpenses.value > 0) r.averageExpenses.inr() else "Not enough history")
        r.movers.forEach { m ->
            KeyValueRow(m.name.substringAfterLast(':'), "${m.current.formatInr()} (${m.change.formatInr(signed = true)} on last month)", divider = false)
        }

        Section("How the spending was tagged")
        KeyValueRow("Needs", r.needs.inr())
        KeyValueRow("Wants", r.wants.inr())
        KeyValueRow("Investment", r.investing.inr())
        KeyValueRow("Untagged", r.untagged.inr())
        KeyValueRow("Planned", r.planned.inr())
        KeyValueRow("Unplanned", r.unplanned.inr(), divider = false)

        if (r.budgets.isNotEmpty()) {
            Section("Budgets")
            r.budgets.forEachIndexed { i, b ->
                KeyValueRow(b.category, "${b.spent.formatInr()} of ${b.budget.formatInr()}" + if (b.isOver) " · over" else "", valueColor = if (b.isOver) c.negative else c.ink, divider = i < r.budgets.lastIndex)
            }
        }

        Section("Entries")
        KeyValueRow("Posted, to reconcile", "${r.entries.posted}")
        KeyValueRow("Reconciled", "${r.entries.reconciled}")
        KeyValueRow("Locked", "${r.entries.locked}")
        KeyValueRow("Drafts", "${r.entries.drafts}")
        KeyValueRow("Prior-period adjustments", "${r.entries.adjustments}", divider = false)
    }
}

@Composable
private fun Section(title: String) {
    Column(Modifier.padding(top = 16.dp, bottom = 2.dp)) {
        Text(title, style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted)
    }
}
