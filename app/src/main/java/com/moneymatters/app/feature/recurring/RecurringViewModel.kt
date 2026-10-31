package com.moneymatters.app.feature.recurring

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.recurring.GenerationResult
import com.moneymatters.app.core.recurring.RecurringRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Recurring entries (plan.html → P11): the rules, their form, and booking whatever has come due. */
class RecurringViewModel(
    private val repo: RecurringRepository,
    private val ledger: LedgerRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = ledger.accounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rows: StateFlow<List<RecurringRow>> = combine(repo.rules(), ledger.accounts(), ::buildRows)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var form by mutableStateOf<RecurringForm?>(null)
    var error by mutableStateOf<String?>(null)
        private set

    /** What the last booking run did, shown once at the top of the list. */
    var notice by mutableStateOf<String?>(null)
        private set

    fun startNew() {
        error = null
        form = RecurringForm(start = today())
    }

    fun startEdit(id: String) {
        viewModelScope.launch {
            error = null
            repo.rule(id)?.let { form = RecurringForm.from(it) }
        }
    }

    fun cancel() {
        form = null
        error = null
    }

    fun save(onSaved: () -> Unit) {
        val f = form ?: return
        if (!f.canSave) return
        viewModelScope.launch {
            try {
                val rule = f.toRule()
                if (f.editingId == null) repo.create(rule) else repo.update(rule)
                notice = describe(repo.generateDue(today()))
                form = null
                error = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "Couldn't save the rule."
            } catch (_: Exception) {
                error = "Couldn't save the rule."
            }
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.delete(id)
            form = null
            onDone()
        }
    }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch {
            repo.setActive(id, active)
            if (active) notice = describe(repo.generateDue(today()))
        }
    }

    /** Books anything due now. The app also does this each time it opens. */
    fun runNow() {
        viewModelScope.launch { notice = describe(repo.generateDue(today())) }
    }

    fun dismissNotice() {
        notice = null
    }

    companion object {
        fun describe(r: GenerationResult): String? {
            val parts = buildList {
                if (r.posted > 0) add("${r.posted} posted")
                if (r.drafts > 0) add("${r.drafts} saved as drafts")
                if (r.skippedClosed > 0) add("${r.skippedClosed} skipped (month closed)")
                if (r.rulesSkipped > 0) add("${r.rulesSkipped} rule${if (r.rulesSkipped == 1) "" else "s"} skipped (account missing)")
            }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
    }
}
