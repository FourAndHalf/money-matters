package com.moneymatters.app.core.loan

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.IllegalLifecycleTransitionException
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.money.Paise
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * What a loan account needs beyond its ledger balance. The balance owed always comes from the ledger; this holds the
 * terms. [firstDueDate] is the date of the first EMI still to be paid when the loan was added; later dates follow monthly.
 */
@Entity(tableName = "loans")
data class LoanEntity(
    /** The Loan-kind liability account this describes. */
    @PrimaryKey val accountId: String,
    /** What was originally borrowed, for showing how much has been repaid. */
    val originalPaise: Long,
    /** Annual interest in basis points: 8.5% is 850. */
    val rateBps: Int,
    val emiPaise: Long,
    val firstDueDate: String,
    /** The account EMIs are paid from. */
    val payFromAccountId: String,
)

/** An EMI paid through the app, or a prepayment ([isPrepayment]). It points at the ledger entry it made. */
@Entity(tableName = "loan_payments", indices = [Index("loanAccountId")])
data class LoanPaymentEntity(
    @PrimaryKey val id: String,
    val loanAccountId: String,
    /** The instalment's due date, or the date paid for a prepayment. */
    val dueDate: String,
    val transactionId: String,
    val principalPaise: Long,
    val interestPaise: Long,
    val isPrepayment: Boolean = false,
)

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans") fun loans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loan_payments ORDER BY dueDate") fun payments(): Flow<List<LoanPaymentEntity>>

    @Query("SELECT * FROM loans WHERE accountId = :accountId") suspend fun loan(accountId: String): LoanEntity?

    @Query("SELECT * FROM loan_payments WHERE loanAccountId = :accountId ORDER BY dueDate")
    suspend fun paymentsFor(accountId: String): List<LoanPaymentEntity>

    @Insert suspend fun insert(loan: LoanEntity)

    @Update suspend fun update(loan: LoanEntity)

    @Query("DELETE FROM loans WHERE accountId = :accountId") suspend fun delete(accountId: String)

    @Query("DELETE FROM loan_payments WHERE loanAccountId = :accountId") suspend fun deletePayments(accountId: String)

    @Insert suspend fun insertPayment(payment: LoanPaymentEntity)
}

const val INTEREST_ACCOUNT = "Expenses:Interest"

/** EMI payments that are still in the books: one whose entry was deleted no longer counts as paid. */
fun livePayments(payments: List<LoanPaymentEntity>, existingTransactionIds: Set<String>): List<LoanPaymentEntity> =
    payments.filter { it.transactionId in existingTransactionIds }

/** The due date of the next EMI not yet paid. */
fun nextDue(loan: LoanEntity, live: List<LoanPaymentEntity>): LocalDate {
    val paid = live.filter { !it.isPrepayment }.map { it.dueDate }.toSet()
    val first = LocalDate.parse(loan.firstDueDate)
    var n = 0
    while (dueDate(first, n).toString() in paid) n++
    return dueDate(first, n)
}

/** Loan terms and the EMIs and prepayments paid. Every payment is a balanced entry made through [LedgerRepository]. */
class LoanRepository(
    private val database: MoneyMattersDatabase,
    private val ledger: LedgerRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao get() = database.loanDao()

    fun loans(): Flow<List<LoanEntity>> = dao.loans()
    fun payments(): Flow<List<LoanPaymentEntity>> = dao.payments()

    /** What is still owed on the loan account, from the ledger (liabilities hold credit balances). */
    suspend fun outstanding(accountId: String): Paise = -ledger.accountBalance(accountId)

    suspend fun add(loan: LoanEntity) {
        validate(loan)
        require(dao.loan(loan.accountId) == null) { "This loan already has its terms" }
        dao.insert(loan)
    }

    suspend fun update(loan: LoanEntity) {
        requireNotNull(dao.loan(loan.accountId)) { "No such loan" }
        validate(loan)
        dao.update(loan)
    }

    /** Removes the terms and the payment records; the account and every entry it made stay in the books. */
    suspend fun delete(accountId: String) = ledger.inTransaction {
        dao.deletePayments(accountId)
        dao.delete(accountId)
    }

    /**
     * Pays the next EMI on [date]: the principal reduces the loan, the interest is an expense, and the whole EMI leaves
     * the paying account, in one entry. The split comes from what is really owed now, so earlier prepayments are reflected.
     */
    suspend fun payEmi(accountId: String, date: LocalDate): LoanPaymentEntity = ledger.inTransaction {
        val loan = requireNotNull(dao.loan(accountId)) { "No such loan" }
        val owed = outstanding(accountId)
        if (owed.value <= 0) throw IllegalLifecycleTransitionException("Nothing is owed on this loan")
        val live = livePayments(dao.paymentsFor(accountId), existingIds())
        val split = splitEmi(owed, Paise(loan.emiPaise), loan.rateBps)
        if (split.principal.value <= 0) throw IllegalLifecycleTransitionException("The EMI doesn't cover this month's interest")
        val interestAccount = ledger.createAccountIfAbsent(INTEREST_ACCOUNT, AccountType.Expense)
        val legs = buildList {
            add(PostingLine(accountId, debit = split.principal))
            if (split.interest.value > 0) add(PostingLine(interestAccount, debit = split.interest))
            add(PostingLine(loan.payFromAccountId, credit = split.paid))
        }
        val name = ledger.accountName(accountId)
        val txn = ledger.postTransaction(date, "$name EMI", legs, memo = "EMI due ${nextDue(loan, live)}")
        LoanPaymentEntity(newId(), accountId, nextDue(loan, live).toString(), txn, split.principal.value, split.interest.value).also { dao.insertPayment(it) }
    }

    /** Pays [amount] straight off the balance on [date], with no interest, up to what is owed. */
    suspend fun prepay(accountId: String, amount: Paise, date: LocalDate): LoanPaymentEntity = ledger.inTransaction {
        val loan = requireNotNull(dao.loan(accountId)) { "No such loan" }
        require(amount.value > 0) { "The amount must be more than zero" }
        val owed = outstanding(accountId)
        require(amount.value <= owed.value) { "That is more than is owed" }
        val name = ledger.accountName(accountId)
        val txn = ledger.postTransaction(
            date, "$name prepayment",
            listOf(PostingLine(accountId, debit = amount), PostingLine(loan.payFromAccountId, credit = amount)),
        )
        LoanPaymentEntity(newId(), accountId, date.toString(), txn, amount.value, 0, isPrepayment = true).also { dao.insertPayment(it) }
    }

    /** The account an existing loan's EMIs are paid from. */
    suspend fun payFromOf(accountId: String): String? = dao.loan(accountId)?.payFromAccountId

    private suspend fun existingIds(): Set<String> = ledger.allTransactionIds()

    private suspend fun validate(loan: LoanEntity) {
        require(loan.originalPaise > 0) { "Enter what was borrowed" }
        require(loan.rateBps in 0..10_000) { "The rate must be between 0% and 100%" }
        require(loan.emiPaise > 0) { "Enter the EMI" }
        LocalDate.parse(loan.firstDueDate)
        require(loan.accountId != loan.payFromAccountId) { "Pay from a different account" }
        val account = requireNotNull(database.ledgerDao().getAccount(loan.accountId)) { "No such account" }
        require(account.type == AccountType.Liability && account.kind == AccountKind.Loan) { "Choose a loan account" }
        val from = requireNotNull(database.ledgerDao().getAccount(loan.payFromAccountId)) { "No such account to pay from" }
        require(from.type == AccountType.Asset) { "Pay from a bank or cash account" }
        val owed = outstanding(loan.accountId)
        require(owed.value <= 0 || emiCoversInterest(owed, Paise(loan.emiPaise), loan.rateBps)) { "The EMI doesn't cover a month's interest, so the loan would never end" }
    }
}
