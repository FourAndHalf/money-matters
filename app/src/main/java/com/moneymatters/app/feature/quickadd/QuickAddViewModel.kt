package com.moneymatters.app.feature.quickadd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.ledger.TransactionTags
import com.moneymatters.app.feature.transactions.EditEntryForm
import com.moneymatters.app.feature.transactions.editFormFor
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds one Quick Add entry in progress and posts it to the real ledger, following the onboarding wizard's shape. */
class QuickAddViewModel(
    private val repo: LedgerRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    var form by mutableStateOf(QuickAddForm.empty(today()))
    var isSaving by mutableStateOf(false)
        private set
    var saveFailed by mutableStateOf(false)
        private set

    val snapshot: StateFlow<LedgerSnapshot?> =
        repo.snapshot().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True once [form] has everything [save] needs: a payee, and a balanced set of postings. */
    fun canSave(): Boolean = form.payee.isNotBlank() && isBalanced(postingsFor(form))

    /** The entry being edited from its detail page, if any. */
    var editForm by mutableStateOf<EditEntryForm?>(null)
    var editError by mutableStateOf<String?>(null)
        private set

    fun startEdit(transactionId: String) {
        editError = null
        viewModelScope.launch { editForm = editFormFor(repo.snapshot().first(), transactionId) }
    }

    fun cancelEdit() {
        editForm = null
        editError = null
    }

    /** Writes the edit through [LedgerRepository.edit]; its refusals (closed month, investment entry) show as [editError]. */
    fun saveEdit(onSaved: () -> Unit) {
        val form = editForm ?: return
        if (!form.canSave || isSaving) return
        viewModelScope.launch {
            isSaving = true
            editError = null
            try {
                repo.edit(
                    transactionId = form.transactionId,
                    date = requireNotNull(form.date),
                    payee = form.payee.trim(),
                    postings = form.postings(),
                    memo = form.note.trim().ifBlank { null },
                    tags = form.tags,
                )
                editForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                editError = e.message ?: "Couldn't save the change."
            } finally {
                isSaving = false
            }
        }
    }

    fun reset() {
        form = QuickAddForm.empty(today())
        saveFailed = false
    }

    fun save(asDraft: Boolean, onSaved: () -> Unit) {
        if (!canSave() || isSaving) return
        val postings = postingsFor(form)
        viewModelScope.launch {
            isSaving = true
            saveFailed = false
            try {
                repo.postTransaction(
                    date = form.date,
                    payee = form.payee.trim(),
                    postings = postings,
                    memo = form.note.trim().ifBlank { null },
                    status = if (asDraft) TransactionStatus.Draft else TransactionStatus.Posted,
                    tags = TransactionTags(form.decision, form.plan, form.paymentMethod),
                )
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                saveFailed = true
            } finally {
                isSaving = false
            }
        }
    }
}
