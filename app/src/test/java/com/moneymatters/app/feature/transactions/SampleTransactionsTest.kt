package com.moneymatters.app.feature.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleTransactionsTest {

    @Test fun `every sample transaction's postings are balanced`() {
        SampleTransactions.byId.values.forEach { detail ->
            assertTrue("transaction ${detail.id} (${detail.payee}) does not balance", detail.isBalanced)
        }
    }

    @Test fun `every sample transaction has at least two postings`() {
        SampleTransactions.byId.values.forEach { detail ->
            assertTrue("transaction ${detail.id} has fewer than 2 postings", detail.postings.size >= 2)
        }
    }

    @Test fun `the list state has exactly one summary per detail`() {
        val summaryIds = SampleTransactions.state.groups.flatMap { it.items }.map { it.id }
        assertEquals(SampleTransactions.byId.keys.sorted(), summaryIds.sorted())
    }

    @Test fun `day groups are newest first`() {
        val months = mapOf("Sep" to 9, "Oct" to 10)
        fun key(label: String): Int {
            val (day, mon) = label.split(" ")
            return months.getValue(mon) * 100 + day.toInt()
        }
        val keys = SampleTransactions.state.groups.map { key(it.dateLabel) }
        assertEquals(keys.sortedDescending(), keys)
    }

    @Test fun `the suspense count matches the drafted uncategorized entry`() {
        assertEquals(1, SampleTransactions.state.uncategorizedCount)
    }

    @Test fun `the sample set covers every status`() {
        val statuses = SampleTransactions.byId.values.map { it.status }.toSet()
        assertEquals(TxnStatus.entries.toSet(), statuses)
    }
}
