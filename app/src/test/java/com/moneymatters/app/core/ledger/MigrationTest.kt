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
            assertEquals(5, db.openHelper.readableDatabase.version) // 1 → 2 → 3 → 4 → 5 in one open
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
            assertEquals(5, db.openHelper.readableDatabase.version)
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
            assertEquals(5, db.openHelper.readableDatabase.version)
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
            assertEquals(5, db.openHelper.readableDatabase.version)
        }
        db.close()
    }
}
