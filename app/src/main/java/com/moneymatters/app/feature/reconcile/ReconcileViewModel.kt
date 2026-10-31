package com.moneymatters.app.feature.reconcile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.reconcile.ReconcileMismatchException
import com.moneymatters.app.core.reconcile.ReconcileRepository
import com.moneymatters.app.core.reconcile.ReconcileView
import com.moneymatters.app.core.reconcile.reconcileView
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Reconciliation (plan.html → P10) for one account at a time. */
class ReconcileViewModel(
    private val ledger: LedgerRepository,
    private val repo: ReconcileRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val accountId = MutableStateFlow<String?>(null)

    /** The account's entries awaiting a statement, live: an entry added while this is open appears here. */
    val view: StateFlow<ReconcileView?> = combine(ledger.snapshot(), accountId) { l, id -> id?.let { reconcileView(l, it) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var statementText by mutableStateOf("")
    var ticked by mutableStateOf<Set<String>>(emptySet())
        private set
    var noteText by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set

    fun open(id: String) {
        statementText = ""
        ticked = emptySet()
        noteText = ""
        error = null
        accountId.value = id
    }

    fun close() {
        accountId.value = null
    }

    fun toggle(transactionId: String) {
        ticked = if (transactionId in ticked) ticked - transactionId else ticked + transactionId
    }

    fun tickAll(all: Boolean) {
        ticked = if (all) view.value?.lines?.map { it.transactionId }?.toSet().orEmpty() else emptySet()
    }

    /** Books the difference to Unaccounted with the typed note, and ticks the new entry so the books then agree. */
    fun postDifference() {
        val v = view.value ?: return
        val statement = parseSignedRupees(statementText) ?: return
        val difference = v.difference(statement, ticked)
        viewModelScope.launch {
            try {
                val id = repo.postDifference(v.accountId, difference, noteText, today())
                ticked = ticked + id
                noteText = ""
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (_: Exception) {
                error = "Couldn't post the difference."
            }
        }
    }

    fun finish(onDone: (Int) -> Unit) {
        val v = view.value ?: return
        val statement = parseSignedRupees(statementText) ?: return
        viewModelScope.launch {
            try {
                val n = repo.finish(v.accountId, statement, ticked)
                ticked = emptySet()
                error = null
                onDone(n)
            } catch (e: CancellationException) {
                throw e
            } catch (_: ReconcileMismatchException) {
                error = "The balance no longer matches. Check the entries again."
            } catch (_: Exception) {
                error = "Couldn't reconcile."
            }
        }
    }
}
