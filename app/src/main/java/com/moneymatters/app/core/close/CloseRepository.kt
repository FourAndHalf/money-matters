package com.moneymatters.app.core.close

import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.ledger.AuditLogEntity
import com.moneymatters.app.core.ledger.ClosedPeriodEntity
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.TransactionStatus
import java.time.LocalDate

class CloseNotAllowedException(message: String) : IllegalStateException(message)

/** Reconciles and then closes a period (plan.html → P17). A close is irreversible: its entries lock and its hash joins the chain. */
class CloseRepository(
    private val database: MoneyMattersDatabase,
    private val ledger: LedgerRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /** Posted → Reconciled for every booked entry in the period, once the investor has confirmed the balances. Returns how many. */
    suspend fun reconcileAll(period: Period): Int = ledger.inTransaction {
        val posted = database.ledgerDao().getAllTransactions().filter { it.status == TransactionStatus.Posted && LocalDate.parse(it.date) in period }
        posted.forEach { ledger.reconcile(it.id) }
        posted.size
    }

    /**
     * Locks every reconciled entry in [period] and chains the close. [typedLabel] must be the period's name. The
     * rules are checked again here, whatever the screen showed, so nothing but a fully reconciled, ended, in-order month can close.
     */
    suspend fun close(period: Period, typedLabel: String, today: LocalDate) {
        if (!typedLabel.trim().equals(period.label, ignoreCase = true)) throw CloseNotAllowedException("Type ${period.label} to confirm")
        if (!today.isAfter(period.end)) throw CloseNotAllowedException("${period.label} hasn't ended yet")
        ledger.inTransaction {
            val dao = database.ledgerDao()
            val closed = dao.getAllClosedPeriods()
            closed.lastOrNull()?.let { if (!LocalDate.parse(it.end).isBefore(period.start)) throw CloseNotAllowedException("${period.label} is already closed or comes before one that is") }
            val txns = dao.getAllTransactions()
            val earlierOpen = txns.count { LocalDate.parse(it.date).isBefore(period.start) && it.status in listOf(TransactionStatus.Draft, TransactionStatus.Posted, TransactionStatus.Reconciled) }
            if (earlierOpen > 0) throw CloseNotAllowedException("Close the earlier months first")
            val inPeriod = txns.filter { LocalDate.parse(it.date) in period }
            if (inPeriod.any { it.status == TransactionStatus.Draft || it.status == TransactionStatus.Posted }) throw CloseNotAllowedException("Every entry must be reconciled first")
            inPeriod.filter { it.status == TransactionStatus.Reconciled }.forEach { ledger.lock(it.id) }

            val invest = database.investDao()
            val snapshotRows = InvestSnapshot(invest.getAllHoldings(), invest.getAllLots(), invest.getAllConsumptions(), invest.getAllPrices())
            val view = LedgerSnapshot(dao.getAllAccounts(), dao.getAllTransactions(), dao.getAllPostings(), emptyList())
            val prices = priceSnapshot(view, snapshotRows, period)
            val prev = closed.lastOrNull()?.hash ?: GENESIS_HASH
            val hash = chainHash(prev, period, canonicalText(view, period), prices)
            dao.insertClosedPeriod(ClosedPeriodEntity(period.start.toString(), period.end.toString(), nowMillis(), prev, hash, prices))
            dao.insertAuditLog(AuditLogEntity(whenMillis = nowMillis(), transactionId = null, action = "MonthClosed", summary = "${period.label} closed", after = hash))
        }
    }
}
