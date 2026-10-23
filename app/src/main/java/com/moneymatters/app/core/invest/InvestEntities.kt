package com.moneymatters.app.core.invest

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.TransactionEntity

enum class Instrument { Stock, MutualFund, Bond }

/** A mutual fund's tax class; [Elss] adds a 3-year lock per instalment. Null for stocks. */
enum class FundClass {
    Equity,
    /** A hybrid fund with at least 65% in equity, taxed like equity. */
    EquityHybrid,
    /** Any other hybrid fund. */
    Hybrid,
    Debt,
    Elss,
}

/** Equity-oriented holdings (stocks, equity and ELSS funds, equity-oriented hybrids) use the listed-equity holding period. */
val HoldingEntity.isEquityOriented: Boolean
    get() = instrument == Instrument.Stock || fundClass == FundClass.Equity || fundClass == FundClass.Elss || fundClass == FundClass.EquityHybrid

enum class CorporateActionKind { Split, Bonus, Dividend, Coupon }

/** plan.html's price badges: only [Verified] and a confirmed [Manual] price are allowed at month-end close. */
enum class PriceStatus { Verified, Manual, Mismatch, Stale, Failed, Suspicious }

/** The instrument details of a stock, fund or bond; its units and cost live on the linked asset account's postings and lots. */
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
    /** Bonds only: the yearly coupon in basis points, and the date the bond is redeemed. */
    val couponBps: Int? = null,
    val maturityDate: String? = null,
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
    /** The entry that opened it, so reversing that entry withdraws the deposit. */
    val openTransactionId: String? = null,
    /** Set when matured, broken or renewed; [payoutPaise] is what came out of it after TDS. */
    val closedOn: String? = null,
    val payoutPaise: Long? = null,
    val closeTransactionId: String? = null,
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
    /** For a split or bonus: new (or bonus) units per unit held, in micro-units (a 1:5 split is 5,000,000). */
    val ratioMicro: Long? = null,
    /** For a dividend: the gross amount, before TDS. */
    val amountPaise: Long? = null,
    /** The ledger entry this action wrote, so reversing that entry also withdraws the action. */
    val transactionId: String? = null,
)

/**
 * Money sent for a fund purchase that has not been allotted units yet: it waits in `Assets:Investments:In Transit`
 * (plan.html → "SIP via In Transit") until the units and NAV are known. Open while neither allotted nor cancelled.
 */
@Entity(
    tableName = "pending_allotments",
    foreignKeys = [
        ForeignKey(HoldingEntity::class, ["id"], ["holdingId"]),
        ForeignKey(TransactionEntity::class, ["id"], ["orderTransactionId"]),
    ],
    indices = [Index("holdingId"), Index("orderTransactionId")],
)
data class PendingAllotmentEntity(
    @PrimaryKey val id: String,
    val holdingId: String,
    val orderDate: String,
    val amountPaise: Long,
    val orderTransactionId: String,
    val allotmentTransactionId: String? = null,
    val cancelTransactionId: String? = null,
    /** Set when a SIP plan proposed this order. */
    val sipPlanId: String? = null,
) {
    val isOpen: Boolean get() = allotmentTransactionId == null && cancelTransactionId == null
}

/** A SIP instalment the investor chose not to pay. It stops nagging but still counts as missed for the goal. */
@Entity(
    tableName = "sip_skips",
    primaryKeys = ["planId", "dueDate"],
    foreignKeys = [ForeignKey(SipPlanEntity::class, ["id"], ["planId"])],
)
data class SipSkipEntity(val planId: String, val dueDate: String)

/** One attempt to download a price file, kept so a failure is never hidden behind an old price (plan.html → P15). */
@Entity(tableName = "price_fetch_log")
data class PriceFetchLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** `nse` or `amfi`. */
    val source: String,
    val attemptedAt: Long,
    val ok: Boolean,
    val httpStatus: Int? = null,
    val rowsParsed: Int = 0,
    val rowsMatched: Int = 0,
    /** A plain-language result or reason. */
    val message: String,
    /** The trading day the prices were for, when the download worked. */
    val priceDate: String? = null,
)
