package com.moneymatters.app.core.invest

import androidx.room.withTransaction
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionTags
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.impliedPrice
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Buys and sells for stocks and mutual funds. Each writes one balanced ledger entry through
 * [LedgerRepository] and the lot bookkeeping in the same database transaction, so the books and the
 * lots cannot drift apart. A trade's amount is the broker's net rupee figure; units and price are detail.
 */
class InvestRepository(
    private val database: MoneyMattersDatabase,
    private val ledger: LedgerRepository,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao get() = database.investDao()

    /** Every holding, lot, sale and price at one moment, re-read after any investment write. */
    fun snapshot(): Flow<InvestSnapshot> =
        database.invalidationTracker.createFlow("holdings", "lots", "lot_consumptions", "prices").map {
            database.withTransaction {
                InvestSnapshot(dao.getAllHoldings(), dao.getAllLots(), dao.getAllConsumptions(), dao.getAllPrices())
            }
        }

    /**
     * Finds a stock by ISIN, then symbol, or creates it with its own asset account. The account is
     * named for the company when given, else the symbol, and is an Investment-kind asset.
     */
    suspend fun findOrCreateStock(symbol: String, isin: String? = null, name: String? = null): String = ledger.inTransaction {
        val existing = isin?.let { dao.holdingByIsin(it) } ?: dao.holdingBySymbol(symbol)
        if (existing != null) return@inTransaction existing.id
        val accountName = if (name.isNullOrBlank()) symbol else "$name ($symbol)"
        val accountId = ledger.createAccount(accountName, AccountType.Asset, kind = AccountKind.Investment)
        addHolding(accountId, Instrument.Stock, isin = isin, symbol = symbol)
    }

    /**
     * Rebuilds lots and past sales from a trade history, all or nothing. Trades settle against
     * [settlementAccountId] (for history, `Equity:Opening`, so no bank balance moves). Sales are replayed
     * in date order against FIFO lots; one that exceeds the units held on its date aborts the whole import.
     */
    suspend fun importTrades(rows: List<TradeRow>, settlementAccountId: String, rules: CapitalGainsRules): Int = ledger.inTransaction {
        for (row in rows.inLedgerOrder()) {
            val holdingId = findOrCreateStock(row.symbol, row.isin, row.name)
            val price = impliedPrice(row.units, row.amount)
            when (row.side) {
                TradeSide.Buy -> buy(holdingId, row.date, row.units, price, row.amount, settlementAccountId, "Buy ${row.symbol}")
                TradeSide.Sell -> sell(holdingId, row.date, row.units, price, row.amount, settlementAccountId, "Sell ${row.symbol}", rules.listedEquity(row.date))
            }
        }
        rows.size
    }

    suspend fun addHolding(
        accountId: String,
        instrument: Instrument,
        isin: String? = null,
        symbol: String? = null,
        fundClass: FundClass? = null,
        exitLoadDays: Int? = null,
        exitLoadBps: Int? = null,
    ): String {
        val id = newId()
        dao.insertHolding(HoldingEntity(id, accountId, instrument, isin, symbol, fundClass, exitLoadDays, exitLoadBps))
        return id
    }

    /** (symbol, holding id) for every holding that has a symbol. */
    suspend fun holdingSymbols(): List<Pair<String, String>> = dao.getAllHoldings().mapNotNull { h -> h.symbol?.let { it to h.id } }

    /** Lots with what is still unsold, oldest first. */
    suspend fun openLots(holdingId: String): List<OpenLot> {
        val used = dao.consumptionsForHolding(holdingId).groupBy { it.lotId }
        return dao.lotsForHolding(holdingId).map { lot ->
            val consumed = used[lot.id].orEmpty()
            OpenLot(
                id = lot.id,
                acquiredOn = LocalDate.parse(lot.acquiredOn),
                remainingUnits = Units(lot.units - consumed.sumOf { it.units }),
                remainingCost = Paise(lot.costPaise - consumed.sumOf { it.costPaise }),
            )
        }
    }

    suspend fun unitsHeld(holdingId: String): Units = openLots(holdingId).fold(Units.Zero) { acc, lot -> acc + lot.remainingUnits }

    /** Pays [amount] (net of every charge) from [fromAccountId] for [units] at [price], and opens a lot for them. */
    suspend fun buy(
        holdingId: String,
        date: LocalDate,
        units: Units,
        price: UnitPrice,
        amount: Paise,
        fromAccountId: String,
        payee: String,
        lockUntil: LocalDate? = null,
        memo: String? = null,
        tags: TransactionTags = TransactionTags(),
    ): String {
        require(units.micro > 0) { "Units bought must be positive" }
        val holding = requireHolding(holdingId)
        return ledger.inTransaction {
            val id = ledger.postTransaction(
                date, payee,
                listOf(
                    PostingLine(holding.accountId, debit = amount, units = units, unitPrice = price),
                    PostingLine(fromAccountId, credit = amount),
                ),
                memo = memo, tags = tags,
            )
            dao.insertLot(LotEntity(newId(), holdingId, date.toString(), units.micro, amount.value, lockUntil?.toString(), sourceTransactionId = id))
            id
        }
    }

    /**
     * Sells [units] first-in-first-out from lots bought on or before [date], paying [proceeds]
     * (net of every charge) into [toAccountId]. The gain or loss is posted to a capital-gains account
     * per term, split by each lot's own holding period. [rule] is the holding-period tax data.
     */
    suspend fun sell(
        holdingId: String,
        date: LocalDate,
        units: Units,
        price: UnitPrice,
        proceeds: Paise,
        toAccountId: String,
        payee: String,
        rule: HoldingPeriodRule,
        memo: String? = null,
        tags: TransactionTags = TransactionTags(),
    ): String {
        require(proceeds.value > 0) { "Sale proceeds must be positive" }
        val holding = requireHolding(holdingId)
        return ledger.inTransaction {
            val eligible = openLots(holdingId).filter { !it.acquiredOn.isAfter(date) }
            val consumed = allocateFifo(eligible, units)
            val cost = Paise(consumed.sumOf { it.cost.value })
            val acquired = eligible.associate { it.id to it.acquiredOn }
            val buckets = splitGain(proceeds, consumed.map { DatedConsumption(it, acquired.getValue(it.lotId)) }, date, rule)

            val postings = mutableListOf(
                PostingLine(toAccountId, debit = proceeds),
                PostingLine(holding.accountId, credit = cost, units = units, unitPrice = price),
            )
            for (bucket in buckets) {
                val gainsAccount = ledger.createAccountIfAbsent(gainAccountName(holding, bucket.term), AccountType.Income)
                postings += if (bucket.gain.value > 0) PostingLine(gainsAccount, credit = bucket.gain) else PostingLine(gainsAccount, debit = -bucket.gain)
            }
            val id = ledger.postTransaction(date, payee, postings, memo = memo, tags = tags)
            dao.insertConsumptions(consumed.map { LotConsumptionEntity(lotId = it.lotId, saleTransactionId = id, units = it.units.micro, costPaise = it.cost.value) })
            id
        }
    }

    suspend fun recordPrice(holdingId: String, date: LocalDate, price: UnitPrice, source: String, status: PriceStatus, fetchedAt: Long = System.currentTimeMillis()) {
        requireHolding(holdingId)
        dao.insertPrice(PriceEntity(holdingId = holdingId, date = date.toString(), price = price.tenThousandths, source = source, status = status, fetchedAt = fetchedAt))
    }

    suspend fun latestPrice(holdingId: String): PriceEntity? = dao.latestPrice(holdingId)

    private suspend fun requireHolding(id: String) = dao.getHolding(id) ?: throw NoSuchElementException("No holding with id $id")

    private fun gainAccountName(holding: HoldingEntity, term: GainTerm): String {
        val equity = holding.instrument == Instrument.Stock || holding.fundClass == FundClass.Equity || holding.fundClass == FundClass.Elss
        return "Income:Capital Gains:${if (term == GainTerm.Short) "STCG" else "LTCG"}:${if (equity) "Equity" else "Other"}"
    }
}

/** Everything the Invest tab reads, in one consistent view. */
data class InvestSnapshot(
    val holdings: List<HoldingEntity>,
    val lots: List<LotEntity>,
    val consumptions: List<LotConsumptionEntity>,
    val prices: List<PriceEntity>,
) {
    companion object { val Empty = InvestSnapshot(emptyList(), emptyList(), emptyList(), emptyList()) }
}
