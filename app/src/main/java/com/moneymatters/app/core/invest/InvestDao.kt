package com.moneymatters.app.core.invest

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface InvestDao {
    @Insert suspend fun insertHolding(holding: HoldingEntity)

    @Query("SELECT * FROM holdings WHERE id = :id")
    suspend fun getHolding(id: String): HoldingEntity?

    @Query("SELECT * FROM holdings WHERE accountId = :accountId")
    suspend fun holdingForAccount(accountId: String): HoldingEntity?

    @Insert suspend fun insertLot(lot: LotEntity)

    @Query("SELECT * FROM lots WHERE holdingId = :holdingId ORDER BY acquiredOn, id")
    suspend fun lotsForHolding(holdingId: String): List<LotEntity>

    @Query("SELECT * FROM lots WHERE sourceTransactionId = :transactionId")
    suspend fun lotsFromTransaction(transactionId: String): List<LotEntity>

    @Query("DELETE FROM lots WHERE id = :lotId")
    suspend fun deleteLot(lotId: String)

    @Insert suspend fun insertConsumptions(consumptions: List<LotConsumptionEntity>)

    @Query("SELECT * FROM lot_consumptions WHERE lotId IN (SELECT id FROM lots WHERE holdingId = :holdingId)")
    suspend fun consumptionsForHolding(holdingId: String): List<LotConsumptionEntity>

    @Query("SELECT COUNT(*) FROM lot_consumptions WHERE lotId = :lotId")
    suspend fun consumptionCountForLot(lotId: String): Int

    @Query("SELECT COUNT(*) FROM lot_consumptions WHERE saleTransactionId = :transactionId")
    suspend fun consumptionCountForSale(transactionId: String): Int

    @Query("DELETE FROM lot_consumptions WHERE saleTransactionId = :transactionId")
    suspend fun deleteConsumptionsForSale(transactionId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPrice(price: PriceEntity)

    @Query("SELECT * FROM prices WHERE holdingId = :holdingId ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun latestPrice(holdingId: String): PriceEntity?

    @Insert suspend fun insertFixedDeposit(deposit: FixedDepositEntity)

    @Query("SELECT * FROM fixed_deposits WHERE accountId = :accountId")
    suspend fun getFixedDeposit(accountId: String): FixedDepositEntity?

    @Insert suspend fun insertSipPlan(plan: SipPlanEntity)

    @Insert suspend fun insertCorporateAction(action: CorporateActionEntity)
}
