package com.moneymatters.app.feature.close

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.budget.BudgetEntity
import com.moneymatters.app.core.budget.BudgetRepository
import com.moneymatters.app.core.close.ChecklistExtras
import com.moneymatters.app.core.close.CloseRepository
import com.moneymatters.app.core.loan.LoanEntity
import com.moneymatters.app.core.loan.LoanPaymentEntity
import com.moneymatters.app.core.loan.LoanRepository
import com.moneymatters.app.core.recurring.RecurringRepository
import com.moneymatters.app.core.recurring.RecurringRuleEntity
import com.moneymatters.app.core.close.owingDeposits
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.ledger.LedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CloseViewModel(
    private val ledger: LedgerRepository,
    private val invest: InvestRepository,
    private val close: CloseRepository,
    private val monthStartDay: () -> Int,
    private val today: () -> LocalDate = LocalDate::now,
    private val recurring: RecurringRepository? = null,
    private val loans: LoanRepository? = null,
    private val budgets: BudgetRepository? = null,
) : ViewModel() {

    /** Balances the investor has confirmed against their statements this session; confirmation isn't kept, a close is done in one sitting. */
    private val confirmed = MutableStateFlow<Set<String>>(emptySet())

    /** Whether the investor has checked holding units against their statement, for this sitting only, like balance confirmation. */
    private val unitsConfirmed = MutableStateFlow(false)

    private val extras = combine(
        recurring?.rules() ?: flowOf(emptyList()), loans?.loans() ?: flowOf(emptyList()),
        loans?.payments() ?: flowOf(emptyList()), budgets?.budgets() ?: flowOf(emptyList()),
    ) { r, l, p, b -> Extras(r, l, p, b) }

    private data class Extras(val rules: List<RecurringRuleEntity>, val loans: List<LoanEntity>, val payments: List<LoanPaymentEntity>, val budgets: List<BudgetEntity>)

    val state: StateFlow<CloseState?> = combine(ledger.snapshot(), invest.snapshot(), confirmed, unitsConfirmed, extras) { l, i, c, u, e ->
        buildClose(l, i, today(), monthStartDay(), c, ChecklistExtras(e.rules, e.loans, e.payments, unitsConfirmed = u), e.budgets)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var typedName by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set

    fun confirm(accountId: String) { confirmed.value = confirmed.value + accountId }

    fun confirmUnits() { unitsConfirmed.value = true }

    /** Books every recurring entry that has come due, the same thing the app does each time it opens. */
    fun bookRecurring() = run { recurring?.generateDue(today()) }

    fun confirmAll() { confirmed.value = confirmed.value + state.value?.balances.orEmpty().map { it.line.accountId } }

    fun reconcile() = run { close.reconcileAll(requireNotNull(state.value?.period)) }

    fun postInterest() = run {
        val period = requireNotNull(state.value?.period)
        owingDeposits(ledger.snapshot().first(), invest.snapshot().first(), period).forEach { (account, _) -> invest.accrueInterest(account, period.end) }
    }

    fun closeMonth() = run {
        val period = requireNotNull(state.value?.period)
        close.close(period, typedName, today())
        typedName = ""
        // Confirmations were for that month's balances; the next month to close starts from none.
        confirmed.value = emptySet()
        unitsConfirmed.value = false
    }

    private fun run(block: suspend () -> Unit) {
        error = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: IllegalStateException) {
                error = e.message
            } catch (e: IllegalArgumentException) {
                error = e.message
            }
        }
    }
}
