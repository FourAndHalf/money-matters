package com.moneymatters.app.feature.quickadd

import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.DecisionKind
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.PaymentMethod
import com.moneymatters.app.core.ledger.PostingEntity
import com.moneymatters.app.core.ledger.TransactionEntity
import com.moneymatters.app.core.ledger.TransactionStatus
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAddTest {

    private val today = LocalDate.of(2026, 10, 20)
    private val bank = AccountEntity("bank", "HDFC Bank", AccountType.Asset, kind = AccountKind.Bank)
    private val card = AccountEntity("card", "HDFC Regalia", AccountType.Liability, kind = AccountKind.CreditCard)
    private val dining = AccountEntity("dining", "Expenses:Dining", AccountType.Expense)
    private val salary = AccountEntity("salary", "Income:Salary", AccountType.Income)
    private val holding = AccountEntity("holding", "Zerodha", AccountType.Asset, kind = AccountKind.Investment)

    @Test fun `Expense moves to a category account from any real account`() {
        assertTrue(EntryKind.Expense.matchesTo(dining))
        assertFalse(EntryKind.Expense.matchesTo(bank))
        assertTrue(EntryKind.Expense.matchesFrom(bank))
        assertTrue(EntryKind.Expense.matchesFrom(card))
        assertFalse(EntryKind.Expense.matchesFrom(dining))
    }

    @Test fun `Card payment only offers a credit card to-side and a real account from-side`() {
        assertTrue(EntryKind.CardPayment.matchesTo(card))
        assertFalse(EntryKind.CardPayment.matchesTo(bank))
        assertTrue(EntryKind.CardPayment.matchesFrom(bank))
    }

    @Test fun `Investment buy and sell only offer investment-kind asset accounts on the investment side`() {
        assertTrue(EntryKind.InvestBuy.matchesTo(holding))
        assertFalse(EntryKind.InvestBuy.matchesTo(bank))
        assertTrue(EntryKind.InvestSell.matchesFrom(holding))
        assertFalse(EntryKind.InvestSell.matchesFrom(bank))
    }

    @Test fun `Income debits the receiving account and credits the income category`() {
        assertTrue(EntryKind.Income.matchesTo(bank))
        assertTrue(EntryKind.Income.matchesFrom(salary))
        assertFalse(EntryKind.Income.matchesFrom(bank))
    }

    @Test fun `postingsFor a simple two-leg form debits the to-account and credits the from-account`() {
        val form = QuickAddForm(kind = EntryKind.Expense, date = today, amount = 486.rupees, toAccountId = dining.id, fromAccountId = bank.id)
        val postings = postingsFor(form)
        assertEquals(2, postings.size)
        assertEquals(486.rupees, postings[0].debit)
        assertEquals(dining.id, postings[0].accountId)
        assertEquals(486.rupees, postings[1].credit)
        assertEquals(bank.id, postings[1].accountId)
    }

    @Test fun `postingsFor is empty until both accounts and a positive amount are set`() {
        assertTrue(postingsFor(QuickAddForm(date = today)).isEmpty())
        assertTrue(postingsFor(QuickAddForm(date = today, toAccountId = dining.id, fromAccountId = bank.id)).isEmpty())
    }

    @Test fun `postingsFor splits uses each line's Dr or Cr text, skipping incomplete rows`() {
        val form = QuickAddForm(
            date = today,
            splits = listOf(
                SplitLine(dining.id, debitText = "100"),
                SplitLine(bank.id, creditText = "40"),
                SplitLine(card.id, creditText = "60"),
                SplitLine(null, debitText = "5"), // no account: skipped
                SplitLine(salary.id), // neither Dr nor Cr: skipped
            ),
        )
        val postings = postingsFor(form)
        assertEquals(3, postings.size)
        assertTrue(isBalanced(postings))
    }

    @Test fun `isBalanced requires at least two legs and equal non-zero debits and credits`() {
        assertFalse(isBalanced(emptyList()))
        val form = QuickAddForm(date = today, amount = 100.rupees, toAccountId = dining.id, fromAccountId = bank.id)
        assertTrue(isBalanced(postingsFor(form)))
    }

    @Test fun `payeeSuggestions ranks by frequency and prefills from the most recent two-leg transaction`() {
        val snapshot = LedgerSnapshot(
            accounts = listOf(bank, dining, salary),
            transactions = listOf(
                TransactionEntity("t1", "2026-10-01", "Swiggy", status = TransactionStatus.Posted, paymentMethod = PaymentMethod.Upi, decision = DecisionKind.Want),
                TransactionEntity("t2", "2026-10-10", "Swiggy", status = TransactionStatus.Posted, paymentMethod = PaymentMethod.Card),
                TransactionEntity("t3", "2026-10-05", "Zomato", status = TransactionStatus.Posted),
            ),
            postings = listOf(
                PostingEntity(1, "t1", dining.id, debitPaise = 300_00),
                PostingEntity(2, "t1", bank.id, creditPaise = 300_00),
                PostingEntity(3, "t2", dining.id, debitPaise = 450_00),
                PostingEntity(4, "t2", bank.id, creditPaise = 450_00),
                PostingEntity(5, "t3", dining.id, debitPaise = 200_00),
                PostingEntity(6, "t3", bank.id, creditPaise = 200_00),
            ),
            audit = emptyList(),
        )
        val suggestions = payeeSuggestions(snapshot)
        assertEquals(listOf("Swiggy", "Zomato"), suggestions.map { it.payee })
        val swiggy = suggestions.first()
        assertEquals(2, swiggy.useCount)
        assertEquals(PaymentMethod.Card, swiggy.paymentMethod) // from t2, the most recent
        assertEquals(dining.id, swiggy.toAccountId)
        assertEquals(bank.id, swiggy.fromAccountId)

        assertEquals(listOf("Zomato"), payeeSuggestions(snapshot, query = "zom").map { it.payee })
        assertTrue(payeeSuggestions(snapshot, query = "nope").isEmpty())
    }
}
