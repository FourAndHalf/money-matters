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

    @Insert suspend fun insertTransaction(transaction: TransactionEntity)

    @Update suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransaction(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun transactions(): Flow<List<TransactionEntity>>

    @Insert suspend fun insertPostings(postings: List<PostingEntity>)

    @Query("DELETE FROM postings WHERE transactionId = :transactionId")
    suspend fun deletePostingsForTransaction(transactionId: String)

    @Query("SELECT * FROM postings WHERE transactionId = :transactionId")
    suspend fun getPostingsForTransaction(transactionId: String): List<PostingEntity>

    @Query("SELECT * FROM postings WHERE accountId = :accountId")
    suspend fun getPostingsForAccount(accountId: String): List<PostingEntity>

    @Insert suspend fun insertAuditLog(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log WHERE transactionId = :transactionId ORDER BY whenMillis")
    fun auditLogForTransaction(transactionId: String): Flow<List<AuditLogEntity>>
}
