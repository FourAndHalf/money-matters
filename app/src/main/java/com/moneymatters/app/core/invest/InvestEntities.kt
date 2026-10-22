package com.moneymatters.app.core.invest

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.TransactionEntity

enum class Instrument { Stock, MutualFund }

/** A mutual fund's tax class; [Elss] adds a 3-year lock per instalment. Null for stocks. */
enum class FundClass { Equity, Debt, Hybrid, Elss }

enum class CorporateActionKind { Split, Bonus, Dividend }

/** plan.html's price badges: only [Verified] and a confirmed [Manual] price are allowed at month-end close. */
enum class PriceStatus { Verified, Manual, Mismatch, Stale, Failed, Suspicious }

/** The instrument details of a stock or fund; its units and cost live on the linked asset account's postings and lots. */
@Entity(
    tableName = "holdings",
    foreignKeys = [ForeignKey(AccountEntity::class, ["id"], ["accountId"])],
    indices = [Index("accountId", unique = true), Index("isin")],
)
data class HoldingEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val instrument: Instrument,
    /** Prices are matched by ISIN, never by name. */
    val isin: String? = null,
    val symbol: String? = null,
    val fundClass: FundClass? = null,
    val exitLoadDays: Int? = null,
    val exitLoadBps: Int? = null,
)

/** One purchase. Never edited: what a sale uses is recorded in [LotConsumptionEntity], so remaining units are derived. */
@Entity(
    tableName = "lots",
    foreignKeys = [
        ForeignKey(HoldingEntity::class, ["id"], ["holdingId"]),
        ForeignKey(TransactionEntity::class, ["id"], ["sourceTransactionId"]),
    ],
    indices = [Index("holdingId"), Index("sourceTransactionId")],
)
data class LotEntity(
    @PrimaryKey val id: String,
    val holdingId: String,
    /** ISO-8601 `yyyy-MM-dd`. */
    val acquiredOn: String,
    val units: Long,
    val costPaise: Long,
    val lockUntil: String? = null,
    /** 31 Jan 2018 value, for grandfathered holdings. */
    val fmv2018Paise: Long? = null,
    val sourceTransactionId: String,
)

@Entity(
    tableName = "lot_consumptions",
    foreignKeys = [
        ForeignKey(LotEntity::class, ["id"], ["lotId"]),
        ForeignKey(TransactionEntity::class, ["id"], ["saleTransactionId"]),
    ],
    indices = [Index("lotId"), Index("saleTransactionId")],
)
data class LotConsumptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lotId: String,
    val saleTransactionId: String,
    val units: Long,
    val costPaise: Long,
)

@Entity(
    tableName = "prices",
    foreignKeys = [ForeignKey(HoldingEntity::class, ["id"], ["holdingId"])],
    indices = [Index("holdingId", "date")],
)
data class PriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val holdingId: String,
    val date: String,
    /** In 1/10,000 rupee. */
    val price: Long,
    val source: String,
    val status: PriceStatus,
    val fetchedAt: Long,
)

@Entity(tableName = "fixed_deposits", foreignKeys = [ForeignKey(AccountEntity::class, ["id"], ["accountId"])])
data class FixedDepositEntity(
    @PrimaryKey val accountId: String,
    val principalPaise: Long,
    val rateBps: Int,
    val compounding: Compounding,
    val startDate: String,
    val maturityDate: String,
    val autoRenew: Boolean = false,
    /** Break penalty as a cut in the rate, in basis points. */
    val penaltyBps: Int = 0,
)

@Entity(
    tableName = "sip_plans",
    foreignKeys = [
        ForeignKey(HoldingEntity::class, ["id"], ["holdingId"]),
        ForeignKey(AccountEntity::class, ["id"], ["fromAccountId"]),
    ],
    indices = [Index("holdingId"), Index("fromAccountId")],
)
data class SipPlanEntity(
    @PrimaryKey val id: String,
    val holdingId: String,
    val amountPaise: Long,
    val dayOfMonth: Int,
    val fromAccountId: String,
    val startDate: String,
    val endDate: String? = null,
    val goalId: String? = null,
)

@Entity(
    tableName = "corporate_actions",
    foreignKeys = [ForeignKey(HoldingEntity::class, ["id"], ["holdingId"])],
    indices = [Index("holdingId")],
)
data class CorporateActionEntity(
    @PrimaryKey val id: String,
    val holdingId: String,
    val kind: CorporateActionKind,
    val date: String,
    val ratioNum: Int? = null,
    val ratioDen: Int? = null,
    val amountPaise: Long? = null,
)
