package com.moneymatters.app.core.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Insert suspend fun insertAccount(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccount(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun findAccountByName(name: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY name")
    fun accounts(): Flow<List<AccountEntity>>

    @Update suspend fun updateAccount(account: AccountEntity)

    @Insert suspend fun insertTransaction(transaction: TransactionEntity)

    @Update suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransaction(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun transactions(): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    @Query("SELECT DISTINCT transactionId FROM postings WHERE accountId = :accountId")
    suspend fun transactionIdsForAccount(accountId: String): List<String>

    @Insert suspend fun insertPostings(postings: List<PostingEntity>)

    @Query("DELETE FROM postings WHERE transactionId = :transactionId")
    suspend fun deletePostingsForTransaction(transactionId: String)

    @Query("SELECT * FROM postings WHERE transactionId = :transactionId")
    suspend fun getPostingsForTransaction(transactionId: String): List<PostingEntity>

    /** Drafts aren't booked yet, so they never count toward a balance. */
    @Query(
        "SELECT p.* FROM postings p JOIN transactions t ON t.id = p.transactionId " +
            "WHERE p.accountId = :accountId AND t.status != 'Draft'",
    )
    suspend fun getBookedPostingsForAccount(accountId: String): List<PostingEntity>

    // Whole-ledger reads for LedgerRepository.snapshot(), which runs them inside one transaction.
    @Query("SELECT * FROM accounts")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM postings")
    suspend fun getAllPostings(): List<PostingEntity>

    @Query("SELECT * FROM audit_log ORDER BY whenMillis, id")
    suspend fun getAllAuditLog(): List<AuditLogEntity>

    @Insert suspend fun insertAuditLog(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log WHERE transactionId = :transactionId ORDER BY whenMillis")
    fun auditLogForTransaction(transactionId: String): Flow<List<AuditLogEntity>>

    @Insert suspend fun insertClosedPeriod(period: ClosedPeriodEntity)

    @Query("SELECT * FROM closed_periods ORDER BY start")
    suspend fun getAllClosedPeriods(): List<ClosedPeriodEntity>

    @Query("SELECT COUNT(*) FROM closed_periods WHERE start <= :date AND end >= :date")
    suspend fun closedPeriodsCovering(date: String): Int

    @Insert suspend fun insertCreditCard(card: CreditCardEntity)

    @Query("SELECT * FROM credit_cards")
    suspend fun getAllCreditCards(): List<CreditCardEntity>
}
