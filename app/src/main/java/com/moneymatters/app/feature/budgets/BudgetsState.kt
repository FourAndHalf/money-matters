package com.moneymatters.app.feature.budgets

import com.moneymatters.app.core.budget.BudgetEntity
import com.moneymatters.app.core.budget.BudgetPeriod
import com.moneymatters.app.core.budget.DateRange
import com.moneymatters.app.core.budget.rangeContaining
import com.moneymatters.app.core.budget.runsOutOn
import com.moneymatters.app.core.budget.spentIn
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.feature.dashboard.BudgetPace
import com.moneymatters.app.feature.dashboard.budgetPace
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One budget measured against its current period. [left] is negative once the budget is overspent. */
data class BudgetRow(
    val id: String,
    val accountId: String,
    val category: String,
    val period: BudgetPeriod,
    val range: DateRange,
    val budget: Paise,
    val spent: Paise,
    val left: Paise,
    val usedFraction: Float,
    val elapsedFraction: Float,
    val pace: BudgetPace,
    val runsOutOn: LocalDate?,
) {
    val isOver: Boolean get() = spent.value > budget.value
}

data class BudgetsState(val rows: List<BudgetRow>, val monthlyBudget: Paise, val monthlySpent: Paise) {
    val hasMonthly: Boolean get() = rows.any { it.period == BudgetPeriod.Monthly }
}

/** "Expenses:Food:Dining" is shown as "Dining"; the full name stays the account's. */
fun categoryLabel(name: String): String = name.substringAfterLast(':')

/** Expense categories a budget can be set on. */
fun budgetableCategories(accounts: List<AccountEntity>): List<AccountEntity> =
    accounts.filter { it.type == AccountType.Expense && !it.isArchived }.sortedBy { it.name.lowercase() }

fun buildBudgets(budgets: List<BudgetEntity>, ledger: LedgerSnapshot, today: LocalDate, monthStartDay: Int): BudgetsState {
    val names = ledger.accounts.associateBy { it.id }
    val rows = budgets.mapNotNull { b ->
        val account = names[b.accountId] ?: return@mapNotNull null
        val range = b.period.rangeContaining(today, monthStartDay)
        val spent = spentIn(b.accountId, ledger.accounts, ledger.transactions, ledger.postings, range)
        val cap = Paise(b.amountPaise)
        val elapsed = ((ChronoUnit.DAYS.between(range.start, today) + 1).toFloat() / range.days).coerceIn(0f, 1f)
        BudgetRow(
            id = b.id, accountId = b.accountId, category = categoryLabel(account.name), period = b.period, range = range,
            budget = cap, spent = spent, left = cap - spent,
            usedFraction = if (cap.value > 0) (spent.value.toFloat() / cap.value).coerceAtLeast(0f) else 0f,
            elapsedFraction = elapsed, pace = budgetPace(spent, cap, elapsed), runsOutOn = runsOutOn(spent, cap, range, today),
        )
    }.sortedWith(compareBy({ it.pace.ordinal * -1 }, { it.period.ordinal }, { it.category.lowercase() }))
    val monthly = rows.filter { it.period == BudgetPeriod.Monthly }
    return BudgetsState(rows, Paise(monthly.sumOf { it.budget.value }), Paise(monthly.sumOf { it.spent.value }))
}

/** The form to set or change a budget. */
data class BudgetForm(
    val editingId: String? = null,
    val accountId: String? = null,
    val amountText: String = "",
    val period: BudgetPeriod = BudgetPeriod.Monthly,
) {
    val amount: Paise? get() = com.moneymatters.app.core.money.parseRupees(amountText)?.takeIf { it.value > 0 }
    val canSave: Boolean get() = accountId != null && amount != null
}
