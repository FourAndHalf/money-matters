package com.moneymatters.app.core.ledger

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.money.Paise
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val DB = "migration-test"
private const val SCHEMAS = "schemas/com.moneymatters.app.core.ledger.MoneyMattersDatabase"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Builds a database exactly as Room created it at [version], from the exported schema JSON. */
    private fun createDatabase(version: Int): SQLiteDatabase {
        val schema = JSONObject(File("$SCHEMAS/$version.json").readText()).getJSONObject("database")
        val db = context.openOrCreateDatabase(DB, Context.MODE_PRIVATE, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        return db
    }

    @Test fun `v1 to v2 keeps every account, entry and balance, with new fields empty`() {
        createDatabase(1).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived) VALUES ('bank', 'HDFC Bank', 'Asset', NULL, 0)")
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived) VALUES ('dining', 'Expenses:Dining', 'Expense', NULL, 0)")
            execSQL("INSERT INTO transactions (id, date, payee, memo, status) VALUES ('t1', '2026-10-19', 'Swiggy', NULL, 'Reconciled')")
            execSQL("INSERT INTO postings (transactionId, accountId, debitPaise, creditPaise) VALUES ('t1', 'dining', 48600, NULL)")
            execSQL("INSERT INTO postings (transactionId, accountId, debitPaise, creditPaise) VALUES ('t1', 'bank', NULL, 48600)")
            execSQL("INSERT INTO audit_log (whenMillis, transactionId, action, summary) VALUES (1, 't1', 'Created', 'Swiggy')")
            close()
        }

        // Opening at the latest version runs the auto-migrations, then Room checks the result against v2's schema and throws on any mismatch.
        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            val repo = LedgerRepository(db)
            assertEquals(Paise(-48600), repo.accountBalance("bank"))
            assertEquals(Paise(48600), repo.accountBalance("dining"))
            val t = db.ledgerDao().getTransaction("t1")!!
            assertEquals(TransactionStatus.Reconciled, t.status)
            assertNull(t.decision)
            assertNull(t.paymentMethod)
            assertNull(db.ledgerDao().getAccount("bank")!!.kind)
            assertEquals(15, db.openHelper.readableDatabase.version) // 1 → 2 → … → 12 in one open
        }
        db.close()
    }

    @Test fun `v2 to v3 keeps every entry and counts existing accounts in net worth`() {
        createDatabase(2).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind) VALUES ('bank', 'HDFC Bank', 'Asset', NULL, 0, 'Bank')")
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind) VALUES ('eq', 'Equity:Opening', 'Equity', NULL, 0, NULL)")
            execSQL("INSERT INTO transactions (id, date, payee, memo, status) VALUES ('t1', '2026-09-30', 'Opening', NULL, 'Posted')")
            execSQL("INSERT INTO postings (transactionId, accountId, debitPaise, creditPaise) VALUES ('t1', 'bank', 5000000, NULL)")
            execSQL("INSERT INTO postings (transactionId, accountId, debitPaise, creditPaise) VALUES ('t1', 'eq', NULL, 5000000)")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(Paise(5_000_000), LedgerRepository(db).accountBalance("bank"))
            assertEquals(true, db.ledgerDao().getAccount("bank")!!.inNetWorth)
            assertNull(db.ledgerDao().getPostingsForTransaction("t1").first().units)
            assertNull(db.investDao().holdingForAccount("bank"))
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v3 to v4 keeps corporate actions and gives them a ratio and an entry link`() {
        createDatabase(3).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth) VALUES ('a1', 'Infosys', 'Asset', NULL, 0, 'Investment', 1)")
            execSQL("INSERT INTO holdings (id, accountId, instrument, symbol) VALUES ('h1', 'a1', 'Stock', 'INFY')")
            execSQL("INSERT INTO corporate_actions (id, holdingId, kind, date, ratioNum, ratioDen, amountPaise) VALUES ('c1', 'h1', 'Dividend', '2026-06-01', NULL, NULL, 180000)")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            val action = db.investDao().getAllCorporateActions().single()
            assertEquals(180_000L, action.amountPaise)
            assertNull(action.ratioMicro)
            assertNull(action.transactionId)
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v4 to v5 adds the In Transit orders table and keeps the holdings`() {
        createDatabase(4).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth) VALUES ('a1', 'PPFAS', 'Asset', NULL, 0, 'Investment', 1)")
            execSQL("INSERT INTO holdings (id, accountId, instrument, fundClass) VALUES ('h1', 'a1', 'MutualFund', 'Equity')")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            assertEquals("h1", db.investDao().holdingForAccount("a1")!!.id)
            assertEquals(0, db.investDao().getAllPending().size)
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v5 to v6 keeps existing fixed deposits and leaves their new fields empty`() {
        createDatabase(5).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth) VALUES ('a1', 'HDFC FD', 'Asset', NULL, 0, 'FixedDeposit', 1)")
            execSQL("INSERT INTO fixed_deposits (accountId, principalPaise, rateBps, compounding, startDate, maturityDate, autoRenew, penaltyBps) VALUES ('a1', 10000000, 700, 'Quarterly', '2026-01-01', '2027-01-01', 0, 100)")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            val fd = db.investDao().getFixedDeposit("a1")!!
            assertEquals(10_000_000L, fd.principalPaise)
            assertNull(fd.closedOn)
            assertNull(fd.openTransactionId)
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v6 to v7 keeps SIP plans and adds a place for skipped instalments`() {
        createDatabase(6).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth) VALUES ('a1', 'PPFAS', 'Asset', NULL, 0, 'Investment', 1)")
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth) VALUES ('bank', 'HDFC', 'Asset', NULL, 0, 'Bank', 1)")
            execSQL("INSERT INTO holdings (id, accountId, instrument, fundClass) VALUES ('h1', 'a1', 'MutualFund', 'Equity')")
            execSQL("INSERT INTO sip_plans (id, holdingId, amountPaise, dayOfMonth, fromAccountId, startDate) VALUES ('s1', 'h1', 1000000, 5, 'bank', '2026-01-01')")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(1_000_000L, db.investDao().getSipPlan("s1")!!.amountPaise)
            assertEquals(0, db.investDao().getAllSipSkips().size)
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v7 to v8 adds the price download log and keeps existing prices`() {
        createDatabase(7).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth) VALUES ('a1', 'Infosys', 'Asset', NULL, 0, 'Investment', 1)")
            execSQL("INSERT INTO holdings (id, accountId, instrument, symbol) VALUES ('h1', 'a1', 'Stock', 'INFY')")
            execSQL("INSERT INTO prices (holdingId, date, price, source, status, fetchedAt) VALUES ('h1', '2026-10-15', 11680000, 'manual', 'Manual', 1)")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(11_680_000L, db.investDao().getAllPrices().single().price)
            assertEquals(0, db.investDao().getAllFetchLog().size)
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v8 to v9 adds month closes and keeps existing entries`() {
        createDatabase(8).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth) VALUES ('a1', 'Bank', 'Asset', NULL, 0, 'Bank', 1)")
            execSQL("INSERT INTO transactions (id, date, payee, memo, status) VALUES ('t1', '2026-10-05', 'Lunch', NULL, 'Posted')")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            assertEquals("Lunch", db.ledgerDao().getTransaction("t1")!!.payee)
            assertEquals(0, db.ledgerDao().getAllClosedPeriods().size)
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v9 to v10 adds credit card details and keeps existing accounts`() {
        createDatabase(9).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth) VALUES ('c1', 'HDFC Regalia', 'Liability', NULL, 0, 'CreditCard', 1)")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            assertEquals("HDFC Regalia", db.ledgerDao().getAccount("c1")!!.name)
            assertEquals(0, db.ledgerDao().getAllCreditCards().size)
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v10 to v11 lets a holding carry a coupon and maturity and keeps existing holdings as they were`() {
        createDatabase(10).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth) VALUES ('a1', 'Infosys (INFY)', 'Asset', NULL, 0, 'Investment', 1)")
            execSQL("INSERT INTO holdings (id, accountId, instrument, isin, symbol, fundClass, exitLoadDays, exitLoadBps) VALUES ('h1', 'a1', 'Stock', NULL, 'INFY', NULL, NULL, NULL)")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            val holding = db.investDao().getHolding("h1")!!
            assertEquals("INFY", holding.symbol)
            assertEquals(null, holding.couponBps)
            assertEquals(null, holding.maturityDate)
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v12 to v13 adds recurring rules and keeps existing entries as they were`() {
        createDatabase(12).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth, isPinned) VALUES ('bank', 'HDFC Bank', 'Asset', NULL, 0, 'Bank', 1, 0)")
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth, isPinned) VALUES ('rent', 'Expenses:Rent', 'Expense', NULL, 0, NULL, 1, 0)")
            execSQL("INSERT INTO transactions (id, date, payee, memo, status) VALUES ('t1', '2026-10-05', 'Rent', NULL, 'Posted')")
            execSQL("INSERT INTO postings (transactionId, accountId, debitPaise, creditPaise) VALUES ('t1', 'rent', 2500000, NULL)")
            execSQL("INSERT INTO postings (transactionId, accountId, debitPaise, creditPaise) VALUES ('t1', 'bank', NULL, 2500000)")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(Paise(-2500000), LedgerRepository(db).accountBalance("bank"))
            assertEquals("Rent", db.ledgerDao().getTransaction("t1")!!.payee)
            assertEquals(0, db.recurringDao().activeRules().size)
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v13 to v14 adds budgets and keeps recurring rules and entries as they were`() {
        createDatabase(13).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth, isPinned) VALUES ('bank', 'HDFC Bank', 'Asset', NULL, 0, 'Bank', 1, 0)")
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth, isPinned) VALUES ('rent', 'Expenses:Rent', 'Expense', NULL, 0, NULL, 1, 0)")
            execSQL("INSERT INTO recurring_rules (id, name, fromAccountId, toAccountId, amountPaise, frequency, startDate, endDate, autoPost, decision, `plan`, paymentMethod, lastGeneratedOn, isActive) VALUES ('r1', 'Rent', 'bank', 'rent', 2500000, 'Monthly', '2026-10-05', NULL, 1, NULL, NULL, NULL, '2026-10-05', 1)")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            assertEquals("Rent", db.recurringDao().get("r1")!!.name)
            assertEquals("Expenses:Rent", db.ledgerDao().getAccount("rent")!!.name)
            assertNull(db.budgetDao().forAccount("rent"))
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }

    @Test fun `v14 to v15 adds loans and keeps budgets and entries as they were`() {
        createDatabase(14).apply {
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth, isPinned) VALUES ('loan', 'Home Loan', 'Liability', NULL, 0, 'Loan', 1, 0)")
            execSQL("INSERT INTO accounts (id, name, type, parentId, isArchived, kind, inNetWorth, isPinned) VALUES ('food', 'Expenses:Food', 'Expense', NULL, 0, NULL, 1, 0)")
            execSQL("INSERT INTO budgets (id, accountId, amountPaise, period) VALUES ('b1', 'food', 1500000, 'Monthly')")
            close()
        }

        val db = Room.databaseBuilder(context, MoneyMattersDatabase::class.java, DB).allowMainThreadQueries().build()
        runBlocking {
            assertEquals(1500000L, db.budgetDao().forAccount("food")!!.amountPaise)
            assertEquals("Home Loan", db.ledgerDao().getAccount("loan")!!.name)
            assertNull(db.loanDao().loan("loan"))
            assertEquals(15, db.openHelper.readableDatabase.version)
        }
        db.close()
    }
}
