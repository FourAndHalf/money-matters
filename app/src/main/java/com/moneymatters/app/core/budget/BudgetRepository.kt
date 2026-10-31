package com.moneymatters.app.core.budget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets") fun budgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id") suspend fun get(id: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE accountId = :accountId") suspend fun forAccount(accountId: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id") suspend fun delete(id: String)
}

/** Standing category budgets. One per category: saving again for the same category changes it. */
class BudgetRepository(
    private val database: MoneyMattersDatabase,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao get() = database.budgetDao()

    fun budgets(): Flow<List<BudgetEntity>> = dao.budgets()

    suspend fun budget(id: String): BudgetEntity? = dao.get(id)

    /** Sets the budget for an expense category, replacing any it already had. Returns its id. */
    suspend fun set(accountId: String, amountPaise: Long, period: BudgetPeriod): String {
        require(amountPaise > 0) { "A budget must be more than zero" }
        val account = requireNotNull(database.ledgerDao().getAccount(accountId)) { "No such category" }
        require(account.type == AccountType.Expense) { "Budgets are for expense categories" }
        val id = dao.forAccount(accountId)?.id ?: newId()
        dao.upsert(BudgetEntity(id, accountId, amountPaise, period))
        return id
    }

    suspend fun delete(id: String) = dao.delete(id)
}
