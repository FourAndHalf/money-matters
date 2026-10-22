package com.moneymatters.app.core.invest

import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.marketValue
import java.time.LocalDate

/** What a holding is worth now: units still held, the cost they carry, and the latest price if there is one. */
data class HoldingValuation(val holding: HoldingEntity, val units: Units, val cost: Paise, val price: PriceEntity?) {
    /** Null until a price exists; callers fall back to [cost] rather than guess. */
    val marketValue: Paise? = price?.let { marketValue(units, UnitPrice(it.price)) }
    val gain: Paise? = marketValue?.let { it - cost }
}

/** Remaining units and cost per holding (lots less what sales consumed), valued at each holding's newest price. */
fun valuations(snapshot: InvestSnapshot): List<HoldingValuation> {
    val used = snapshot.consumptions.groupBy { it.lotId }
    val lotsByHolding = snapshot.lots.groupBy { it.holdingId }
    val latestPrice = snapshot.prices.groupBy { it.holdingId }.mapValues { (_, v) -> v.maxWith(compareBy({ it.date }, { it.id })) }
    return snapshot.holdings.map { holding ->
        val lots = lotsByHolding[holding.id].orEmpty()
        var units = 0L
        var cost = 0L
        for (lot in lots) {
            val consumed = used[lot.id].orEmpty()
            units += lot.units - consumed.sumOf { it.units }
            cost += lot.costPaise - consumed.sumOf { it.costPaise }
        }
        HoldingValuation(holding, Units(units), Paise(cost), latestPrice[holding.id])
    }
}

/**
 * The investor's cash flows for one holding account: money out on each buy (negative), and on each
 * sale the proceeds, which are the cost leaving the holding plus the gain posted beside it. Drafts are ignored.
 */
fun holdingCashFlows(ledger: LedgerSnapshot, accountId: String): List<CashFlow> = holdingFlows(ledger, accountId).map { it.second }

/** Same flows as [holdingCashFlows], each with the id of the transaction it came from. */
fun holdingFlows(ledger: LedgerSnapshot, accountId: String): List<Pair<String, CashFlow>> {
    val accountsById = ledger.accounts.associateBy { it.id }
    val postingsByTxn = ledger.postings.groupBy { it.transactionId }
    return ledger.transactions
        .filter { it.status != TransactionStatus.Draft }
        .mapNotNull { txn ->
            val legs = postingsByTxn[txn.id].orEmpty()
            val own = legs.filter { it.accountId == accountId }
            if (own.isEmpty()) return@mapNotNull null
            val debit = own.sumOf { it.debitPaise ?: 0 }
            val credit = own.sumOf { it.creditPaise ?: 0 }
            val gains = legs.filter { accountsById[it.accountId]?.type == AccountType.Income }
                .sumOf { (it.creditPaise ?: 0) - (it.debitPaise ?: 0) }
            // A buy has only a debit; a sale's proceeds are the cost credited out plus its net gain.
            val flow = if (credit > 0) credit + gains - debit else -debit
            txn.id to CashFlow(LocalDate.parse(txn.date), Paise(flow))
        }
}
