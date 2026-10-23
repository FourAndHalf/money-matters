package com.moneymatters.app.core.invest

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface InvestDao {
    @Insert suspend fun insertHolding(holding: HoldingEntity)

    @Query("SELECT * FROM holdings")
    suspend fun getAllHoldings(): List<HoldingEntity>

    @Query("SELECT * FROM lots")
    suspend fun getAllLots(): List<LotEntity>

    @Query("SELECT * FROM lot_consumptions")
    suspend fun getAllConsumptions(): List<LotConsumptionEntity>

    @Query("SELECT * FROM prices ORDER BY date, id")
    suspend fun getAllPrices(): List<PriceEntity>

    @Query("SELECT * FROM holdings WHERE isin = :isin LIMIT 1")
    suspend fun holdingByIsin(isin: String): HoldingEntity?

    @Query("SELECT * FROM holdings WHERE symbol = :symbol COLLATE NOCASE LIMIT 1")
    suspend fun holdingBySymbol(symbol: String): HoldingEntity?

    @Query("SELECT * FROM holdings WHERE id = :id")
    suspend fun getHolding(id: String): HoldingEntity?

    @Query("SELECT * FROM holdings WHERE accountId = :accountId")
    suspend fun holdingForAccount(accountId: String): HoldingEntity?

    @Update suspend fun updateHolding(holding: HoldingEntity)

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun deleteHolding(id: String)

    @Query("DELETE FROM lot_consumptions WHERE lotId IN (SELECT id FROM lots WHERE holdingId = :holdingId)")
    suspend fun deleteConsumptionsForHolding(holdingId: String)

    @Query("DELETE FROM lots WHERE holdingId = :holdingId")
    suspend fun deleteLotsForHolding(holdingId: String)

    @Query("DELETE FROM pending_allotments WHERE holdingId = :holdingId")
    suspend fun deletePendingForHolding(holdingId: String)

    @Query("DELETE FROM sip_skips WHERE planId IN (SELECT id FROM sip_plans WHERE holdingId = :holdingId)")
    suspend fun deleteSkipsForHolding(holdingId: String)

    @Query("DELETE FROM sip_plans WHERE holdingId = :holdingId")
    suspend fun deleteSipPlansForHolding(holdingId: String)

    @Query("DELETE FROM corporate_actions WHERE holdingId = :holdingId")
    suspend fun deleteCorporateActionsForHolding(holdingId: String)

    @Query("DELETE FROM prices WHERE holdingId = :holdingId")
    suspend fun deletePricesForHolding(holdingId: String)

    @Query("SELECT * FROM lots WHERE id = :lotId")
    suspend fun getLot(lotId: String): LotEntity?

    @Query("DELETE FROM fixed_deposits WHERE accountId = :accountId")
    suspend fun deleteFixedDeposit(accountId: String)

    @Query("DELETE FROM sip_skips WHERE planId = :planId")
    suspend fun deleteSkipsForPlan(planId: String)

    @Query("DELETE FROM sip_plans WHERE id = :planId")
    suspend fun deleteSipPlan(planId: String)

    @Query("UPDATE pending_allotments SET sipPlanId = NULL WHERE sipPlanId = :planId")
    suspend fun detachOrdersFromPlan(planId: String)

    @Query("SELECT * FROM corporate_actions WHERE transactionId = :transactionId")
    suspend fun corporateActionsForTransaction(transactionId: String): List<CorporateActionEntity>

    @Query("SELECT COUNT(*) FROM pending_allotments WHERE allotmentTransactionId = :transactionId")
    suspend fun pendingCountForAllotment(transactionId: String): Int

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

    @Query("SELECT * FROM prices WHERE id = :id")
    suspend fun getPrice(id: Long): PriceEntity?

    @Query("SELECT * FROM prices WHERE holdingId = :holdingId AND date = :date")
    suspend fun pricesFor(holdingId: String, date: String): List<PriceEntity>

    @Query("SELECT * FROM prices WHERE holdingId = :holdingId AND date < :date ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun latestPriceBefore(holdingId: String, date: String): PriceEntity?

    @Update suspend fun updatePrice(price: PriceEntity)

    @Query("DELETE FROM prices WHERE id = :id")
    suspend fun deletePrice(id: Long)

    @Insert suspend fun insertFetchLog(entry: PriceFetchLogEntity)

    @Query("SELECT * FROM price_fetch_log ORDER BY attemptedAt, id")
    suspend fun getAllFetchLog(): List<PriceFetchLogEntity>

    @Insert suspend fun insertFixedDeposit(deposit: FixedDepositEntity)

    @Query("SELECT * FROM fixed_deposits WHERE accountId = :accountId")
    suspend fun getFixedDeposit(accountId: String): FixedDepositEntity?

    @Query("SELECT * FROM fixed_deposits")
    suspend fun getAllFixedDeposits(): List<FixedDepositEntity>

    @Update suspend fun updateFixedDeposit(deposit: FixedDepositEntity)

    /** Reversing a closing entry reopens the deposit. */
    @Query("UPDATE fixed_deposits SET closedOn = NULL, payoutPaise = NULL, closeTransactionId = NULL WHERE closeTransactionId = :transactionId")
    suspend fun reopenDepositsClosedBy(transactionId: String)

    /** Reversing an opening entry withdraws the deposit's record. */
    @Query("DELETE FROM fixed_deposits WHERE openTransactionId = :transactionId")
    suspend fun deleteDepositsOpenedBy(transactionId: String)

    @Insert suspend fun insertSipPlan(plan: SipPlanEntity)

    @Update suspend fun updateSipPlan(plan: SipPlanEntity)

    @Query("SELECT * FROM sip_plans ORDER BY startDate, id")
    suspend fun getAllSipPlans(): List<SipPlanEntity>

    @Query("SELECT * FROM sip_plans WHERE id = :id")
    suspend fun getSipPlan(id: String): SipPlanEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSipSkip(skip: SipSkipEntity)

    @Query("SELECT * FROM sip_skips")
    suspend fun getAllSipSkips(): List<SipSkipEntity>

    @Query("SELECT COUNT(*) FROM pending_allotments WHERE orderTransactionId = :transactionId")
    suspend fun pendingCountForOrder(transactionId: String): Int

    /** Reversing a cancellation puts the order back In Transit. */
    @Query("UPDATE pending_allotments SET cancelTransactionId = NULL WHERE cancelTransactionId = :transactionId")
    suspend fun reopenPendingCancelledBy(transactionId: String)

    @Insert suspend fun insertCorporateAction(action: CorporateActionEntity)

    @Insert suspend fun insertPending(pending: PendingAllotmentEntity)

    @Update suspend fun updatePending(pending: PendingAllotmentEntity)

    @Query("SELECT * FROM pending_allotments WHERE id = :id")
    suspend fun getPending(id: String): PendingAllotmentEntity?

    @Query("SELECT * FROM pending_allotments ORDER BY orderDate, id")
    suspend fun getAllPending(): List<PendingAllotmentEntity>

    /** An allotment that was reversed puts its order back In Transit. */
    @Query("UPDATE pending_allotments SET allotmentTransactionId = NULL WHERE allotmentTransactionId = :transactionId")
    suspend fun reopenPendingForAllotment(transactionId: String)

    @Query("SELECT * FROM corporate_actions ORDER BY date, id")
    suspend fun getAllCorporateActions(): List<CorporateActionEntity>

    @Query("DELETE FROM corporate_actions WHERE transactionId = :transactionId")
    suspend fun deleteCorporateActionsForTransaction(transactionId: String)

    @Query("SELECT COUNT(*) FROM corporate_actions WHERE holdingId = :holdingId AND kind IN ('Split', 'Bonus') AND date > :date")
    suspend fun restatementsAfter(holdingId: String, date: String): Int
}
