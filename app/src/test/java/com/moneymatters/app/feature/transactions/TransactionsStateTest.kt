package com.moneymatters.app.feature.transactions

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.rupees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionsStateTest {

    private fun txn(id: String, amount: Paise, status: TxnStatus = TxnStatus.Posted, account: String = "HDFC Bank", payee: String = "Payee $id", category: String = "Other") =
        TransactionSummary(id, "P", payee, category, account, amount, status, emptyList())

    private val sample = TransactionsState(
        groups = listOf(
            DayGroup("19 Oct", listOf(
                txn("a", (-486).rupees, payee = "Swiggy", category = "Dining"),
                txn("b", (-250).rupees, status = TxnStatus.Draft, account = "Cash", payee = "Misc"),
            )),
            DayGroup("18 Oct", listOf(txn("c", (-2_140).rupees, status = TxnStatus.Reconciled, payee = "BigBasket", category = "Groceries"))),
        ),
        accounts = listOf("HDFC Bank", "Cash"),
    )

    @Test fun `a day's total is the sum of its transactions`() {
        assertEquals((-736).rupees, sample.groups[0].total)
    }

    @Test fun `uncategorized count is zero when nothing is uncategorized`() {
        assertEquals(0, sample.uncategorizedCount)
    }

    @Test fun `an uncategorized transaction is counted`() {
        val withSuspense = sample.copy(
            groups = sample.groups + DayGroup("17 Oct", listOf(txn("d", (-10).rupees, category = "Uncategorized").copy(uncategorized = true))),
        )
        assertEquals(1, withSuspense.uncategorizedCount)
    }

    @Test fun `no filters keeps every group and every item`() {
        val result = sample.filtered(TransactionFilters())
        assertEquals(2, result.size)
        assertEquals(2, result[0].items.size)
    }

    @Test fun `status filter keeps only matching transactions and drops empty days`() {
        val result = sample.filtered(TransactionFilters(status = TxnStatus.Reconciled))
        assertEquals(1, result.size)
        assertEquals("18 Oct", result[0].dateLabel)
        assertEquals("c", result[0].items.single().id)
    }

    @Test fun `account filter matches only that account`() {
        val result = sample.filtered(TransactionFilters(account = "Cash"))
        assertEquals(1, result.size)
        assertEquals("b", result[0].items.single().id)
    }

    @Test fun `search matches payee, category or account, case-insensitively`() {
        assertEquals("a", sample.filtered(TransactionFilters(query = "swiggy")).single().items.single().id)
        assertEquals("c", sample.filtered(TransactionFilters(query = "groceries")).single().items.single().id)
        assertEquals("b", sample.filtered(TransactionFilters(query = "cash")).single().items.single().id)
        assertTrue(sample.filtered(TransactionFilters(query = "nothing matches this")).isEmpty())
    }

    @Test fun `filters combine`() {
        val result = sample.filtered(TransactionFilters(query = "Dining", status = TxnStatus.Posted, account = "HDFC Bank"))
        assertEquals("a", result.single().items.single().id)
    }

    @Test fun `primary action follows the lifecycle status`() {
        assertEquals(TxnAction.Edit, TxnStatus.Draft.primaryAction())
        assertEquals(TxnAction.Edit, TxnStatus.Posted.primaryAction())
        assertEquals(TxnAction.Reverse, TxnStatus.Reconciled.primaryAction())
        assertEquals(TxnAction.PriorPeriodAdjustment, TxnStatus.Locked.primaryAction())
        assertEquals(TxnAction.PriorPeriodAdjustment, TxnStatus.PriorPeriodAdjustment.primaryAction())
    }
}
