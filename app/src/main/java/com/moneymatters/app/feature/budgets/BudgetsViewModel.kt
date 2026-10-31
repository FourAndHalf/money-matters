package com.moneymatters.app.feature.budgets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.budget.BudgetRepository
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.feature.recurring.toPlainRupees
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Budgets (plan.html → P12): spending against each category's budget for the current period. */
class BudgetsViewModel(
    private val repo: BudgetRepository,
    private val ledger: LedgerRepository,
    private val monthStartDay: () -> Int,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    val state: StateFlow<BudgetsState> = combine(repo.budgets(), ledger.snapshot()) { b, l -> buildBudgets(b, l, today(), monthStartDay()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsState(emptyList(), com.moneymatters.app.core.money.Paise.Zero, com.moneymatters.app.core.money.Paise.Zero))

    val categories: StateFlow<List<AccountEntity>> = ledger.accounts().map(::budgetableCategories)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var form by mutableStateOf<BudgetForm?>(null)
    var error by mutableStateOf<String?>(null)
        private set

    fun startNew() {
        error = null
        form = BudgetForm()
    }

    fun startEdit(id: String) {
        viewModelScope.launch {
            error = null
            repo.budget(id)?.let { form = BudgetForm(it.id, it.accountId, com.moneymatters.app.core.money.Paise(it.amountPaise).toPlainRupees(), it.period) }
        }
    }

    fun cancel() {
        form = null
        error = null
    }

    fun save() {
        val f = form ?: return
        val accountId = f.accountId ?: return
        val amount = f.amount ?: return
        viewModelScope.launch {
            try {
                repo.set(accountId, amount.value, f.period)
                form = null
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "Couldn't save the budget."
            } catch (_: Exception) {
                error = "Couldn't save the budget."
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repo.delete(id)
            form = null
        }
    }
}
