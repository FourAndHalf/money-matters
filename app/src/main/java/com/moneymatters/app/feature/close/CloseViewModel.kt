package com.moneymatters.app.feature.close

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.close.CloseRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CloseViewModel(
    private val ledger: LedgerRepository,
    private val invest: InvestRepository,
    private val close: CloseRepository,
    private val monthStartDay: () -> Int,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    /** Balances the investor has confirmed against their statements this session; confirmation isn't kept, a close is done in one sitting. */
    private val confirmed = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<CloseState?> = combine(ledger.snapshot(), invest.snapshot(), confirmed) { l, i, c -> buildClose(l, i, today(), monthStartDay(), c) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var typedName by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set

    fun confirm(accountId: String) { confirmed.value = confirmed.value + accountId }

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
