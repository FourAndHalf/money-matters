package com.moneymatters.app.feature.settings

import android.content.Context
import android.net.Uri
import com.moneymatters.app.core.ledger.LedgerDatabaseProvider
import com.moneymatters.app.core.ledger.LedgerRepository
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Flushes the WAL, then copies the live (still SQLCipher-encrypted) database file to [destination]. */
suspend fun backupDatabase(context: Context, repo: LedgerRepository, destination: Uri) = withContext(Dispatchers.IO) {
    repo.checkpoint()
    val source = LedgerDatabaseProvider.databaseFile(context)
    context.contentResolver.openOutputStream(destination)?.use { out ->
        source.inputStream().use { it.copyTo(out) }
    } ?: error("Couldn't open $destination for writing")
}

/**
 * Overwrites the live database file with [source], then kills the process outright: the restored
 * file only decrypts under this device's Keystore-held passphrase (it isn't portable across
 * installs), and hot-swapping a live Room connection risks leaving the UI reading from a closed
 * database. The next launch starts clean and reads the restored file.
 */
suspend fun restoreDatabase(context: Context, source: Uri) = withContext(Dispatchers.IO) {
    val destination = LedgerDatabaseProvider.databaseFile(context)
    context.contentResolver.openInputStream(source)?.use { input ->
        destination.outputStream().use { out -> input.copyTo(out) }
    } ?: error("Couldn't open the chosen file")
    Runtime.getRuntime().exit(0)
}

/** A flat CSV of every posting, one row per leg (plan.html → P21's "export"). */
suspend fun exportCsv(repo: LedgerRepository): String {
    val snapshot = repo.snapshot().first()
    val accountsById = snapshot.accounts.associateBy { it.id }
    val transactionsById = snapshot.transactions.associateBy { it.id }
    val rows = StringBuilder("Date,Payee,Status,Account,Debit,Credit,Memo\n")
    for (posting in snapshot.postings.sortedBy { transactionsById[it.transactionId]?.date }) {
        val txn = transactionsById[posting.transactionId] ?: continue
        val account = accountsById[posting.accountId]?.name ?: posting.accountId
        rows.append(
            listOf(
                txn.date,
                txn.payee.csvField(),
                txn.status.name,
                account.csvField(),
                posting.debitPaise?.toPlainRupees() ?: "",
                posting.creditPaise?.toPlainRupees() ?: "",
                (txn.memo ?: "").csvField(),
            ).joinToString(","),
        )
        rows.append('\n')
    }
    return rows.toString()
}

private fun Long.toPlainRupees(): String {
    val sign = if (this < 0) "-" else ""
    val value = abs(this)
    return "$sign${value / 100}.${(value % 100).toString().padStart(2, '0')}"
}

private fun String.csvField(): String =
    if (contains(',') || contains('"') || contains('\n')) "\"" + replace("\"", "\"\"") + "\"" else this
