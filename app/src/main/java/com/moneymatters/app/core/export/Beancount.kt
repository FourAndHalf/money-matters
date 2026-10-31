package com.moneymatters.app.core.export

import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.ledger.TransactionStatus
import java.time.LocalDate
import java.util.Locale

private val ROOTS = mapOf(
    AccountType.Asset to "Assets", AccountType.Liability to "Liabilities", AccountType.Equity to "Equity",
    AccountType.Income to "Income", AccountType.Expense to "Expenses",
)

/** One component of a Beancount account name: letters, digits and dashes, starting with a capital or a digit. */
private fun component(raw: String): String {
    val dashed = raw.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')
    if (dashed.isEmpty()) return "Account"
    return dashed.replaceFirstChar { it.uppercaseChar() }
}

/**
 * Beancount names for every account, unique and valid: the type becomes the root (Assets, Liabilities, Equity, Income, Expenses),
 * a name already starting with its own root, as "Expenses:Food" does, isn't doubled, and two names that come out alike are numbered.
 */
fun beancountNames(accounts: List<AccountEntity>): Map<String, String> {
    val used = HashSet<String>()
    return accounts.sortedBy { it.name.lowercase() }.associate { a ->
        val root = ROOTS.getValue(a.type)
        val parts = a.name.split(':').map { it.trim() }.filter { it.isNotEmpty() }.let { if (it.firstOrNull().equals(root, ignoreCase = true)) it.drop(1) else it }
        var name = (listOf(root) + parts.map(::component).ifEmpty { listOf("Account") }).joinToString(":")
        var n = 2
        val base = name
        while (!used.add(name)) name = "$base-${n++}"
        a.id to name
    }
}

private fun amount(paise: Long): String {
    val sign = if (paise < 0) "-" else ""
    val a = kotlin.math.abs(paise)
    return String.format(Locale.ROOT, "%s%d.%02d INR", sign, a / 100, a % 100)
}

private fun quoted(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

/**
 * The whole ledger as Beancount text (plan.html → P18's "Beancount" export): an `open` line for each account that is used,
 * dated its first use, and every entry with its postings. Drafts are flagged `!` rather than `*`. Amounts are in rupees; units and
 * prices on holdings are written as comments, since a Beancount commodity needs a price history this ledger doesn't hold.
 */
fun beancount(ledger: LedgerSnapshot): String {
    val names = beancountNames(ledger.accounts)
    val postingsByTxn = ledger.postings.groupBy { it.transactionId }
    val txns = ledger.transactions.sortedWith(compareBy({ it.date }, { it.id }))
    val firstUse = HashMap<String, String>()
    for (t in txns) postingsByTxn[t.id].orEmpty().forEach { firstUse.putIfAbsent(it.accountId, t.date) }

    val out = StringBuilder()
    out.append("; Money Matters export\n; Amounts are in rupees; entries marked ! are drafts.\n")
    out.append("option \"title\" \"Money Matters\"\noption \"operating_currency\" \"INR\"\n\n")
    firstUse.entries.sortedWith(compareBy({ it.value }, { names[it.key] })).forEach { (id, date) -> out.append("$date open ${names.getValue(id)} INR\n") }
    for (t in txns) {
        val flag = if (t.status == TransactionStatus.Draft) "!" else "*"
        out.append("\n${LocalDate.parse(t.date)} $flag ${quoted(t.payee)}")
        t.memo?.takeIf { it.isNotBlank() }?.let { out.append(" ${quoted(it)}") }
        out.append("\n")
        t.decision?.let { out.append("  decision: ${quoted(it.name)}\n") }
        t.plan?.let { out.append("  plan: ${quoted(it.name)}\n") }
        t.paymentMethod?.let { out.append("  payment: ${quoted(it.name)}\n") }
        out.append("  status: ${quoted(t.status.name)}\n")
        for (p in postingsByTxn[t.id].orEmpty().sortedBy { it.id }) {
            val signed = (p.debitPaise ?: 0L) - (p.creditPaise ?: 0L)
            out.append("  ${names.getValue(p.accountId)}  ${amount(signed)}")
            if (p.units != null && p.unitPrice != null) out.append("  ; ${p.units!! / 1_000_000.0} units at ${p.unitPrice!! / 10_000.0}")
            out.append("\n")
        }
    }
    return out.toString()
}
