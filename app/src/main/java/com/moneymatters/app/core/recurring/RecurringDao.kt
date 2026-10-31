package com.moneymatters.app.core.recurring

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_rules ORDER BY name COLLATE NOCASE")
    fun rules(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE isActive = 1 ORDER BY startDate, id")
    suspend fun activeRules(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun get(id: String): RecurringRuleEntity?

    @Insert suspend fun insert(rule: RecurringRuleEntity)

    @Update suspend fun update(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun delete(id: String)
}
