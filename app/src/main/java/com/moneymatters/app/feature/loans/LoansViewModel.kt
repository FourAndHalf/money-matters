package com.moneymatters.app.feature.loans

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.loan.LoanRepository
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.parseRupees
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

/** Loans: EMI schedules, interest paid, and paying an EMI or prepaying. */
class LoansViewModel(
    private val repo: LoanRepository,
    private val ledger: LedgerRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    val state: StateFlow<LoansState> = combine(repo.loans(), repo.payments(), ledger.snapshot()) { l, p, s -> buildLoans(l, p, s).asOf(today()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoansState(emptyList(), emptyMap()))

    /** Loan accounts with no terms yet, which the form offers to describe. */
    val undescribed: StateFlow<List<AccountEntity>> = combine(ledger.accounts(), repo.loans()) { accounts, loans ->
        val have = loans.map { it.accountId }.toSet()
        accounts.filter { it.type == AccountType.Liability && it.kind == AccountKind.Loan && !it.isArchived && it.id !in have }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val payFromAccounts: StateFlow<List<AccountEntity>> = ledger.accounts()
        .map { list -> list.filter { it.type == AccountType.Asset && !it.isArchived && it.kind in setOf(AccountKind.Bank, AccountKind.Cash) }.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var form by mutableStateOf<LoanForm?>(null)
    var openAccountId by mutableStateOf<String?>(null)
    var prepayText by mutableStateOf("")
    var paidOn by mutableStateOf(today())
    var error by mutableStateOf<String?>(null)
        private set

    fun startNew() {
        error = null
        form = LoanForm(firstDue = today().plusMonths(1))
    }

    /** Picks the loan account, and starts "what was borrowed" at what is owed now, which is right for a loan just being added. */
    fun pickAccount(id: String) {
        val f = form ?: return
        viewModelScope.launch {
            val owed = repo.outstanding(id)
            form = f.copy(accountId = id, originalText = if (f.originalText.isBlank() && owed.value > 0) owed.toPlainRupees() else f.originalText)
        }
    }

    fun startEdit(accountId: String) {
        val d = state.value.details[accountId] ?: return
        val r = d.row
        error = null
        form = LoanForm(
            firstDue = r.nextDue ?: today(), editingAccountId = accountId, accountId = accountId, originalText = r.original.toPlainRupees(),
            rateText = "${r.rateBps / 100}.${(r.rateBps % 100).toString().padStart(2, '0')}", emiText = r.emi.toPlainRupees(),
            payFromId = null,
        )
        viewModelScope.launch { form = form?.copy(payFromId = repo.payFromOf(accountId)) }
    }

    fun cancelForm() {
        form = null
        error = null
    }

    fun save() {
        val f = form ?: return
        if (!f.canSave) return
        launch {
            val loan = f.toLoan()
            if (f.editingAccountId != null) repo.update(loan) else repo.add(loan)
            form = null
        }
    }

    fun payEmi() {
        val id = openAccountId ?: return
        launch { repo.payEmi(id, paidOn) }
    }

    fun prepay() {
        val id = openAccountId ?: return
        val amount = parseRupees(prepayText)?.takeIf { it.value > 0 } ?: return
        launch {
            repo.prepay(id, amount, paidOn)
            prepayText = ""
        }
    }

    fun deleteTerms() {
        val id = openAccountId ?: return
        launch {
            repo.delete(id)
            openAccountId = null
            form = null
        }
    }

    fun open(accountId: String) {
        error = null
        prepayText = ""
        paidOn = today()
        openAccountId = accountId
    }

    fun back() {
        when {
            form != null -> cancelForm()
            openAccountId != null -> openAccountId = null
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (e: IllegalStateException) {
                error = e.message
            } catch (_: Exception) {
                error = "Something went wrong."
            }
        }
    }
}
