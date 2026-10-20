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

        // Opening at v2 runs the auto-migration, then Room checks the result against v2's schema and throws on any mismatch.
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
            assertEquals(2, db.openHelper.readableDatabase.version)
        }
        db.close()
    }
}
