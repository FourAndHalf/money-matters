package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.UnitPrice
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.zip.ZipInputStream

private val AMFI_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)

/** One scheme line of AMFI's daily NAV file. A fund has up to two ISINs (payout/growth, and dividend reinvestment). */
data class AmfiRow(
    val schemeCode: String,
    val isins: List<String>,
    val name: String,
    /** Null when the NAV isn't a number ("N.A."). Zero is kept so callers can report it. */
    val nav: UnitPrice?,
    /** The row's own NAV date; wound-up and frozen schemes keep an old one. */
    val date: LocalDate?,
)

/** A NAV or price string rounded half-up to the four decimals [UnitPrice] holds, without going through a float. */
internal fun parsePriceText(text: String): UnitPrice? {
    val value = text.trim().toBigDecimalOrNull() ?: return null
    if (value.signum() < 0) return null
    return UnitPrice(value.setScale(4, RoundingMode.HALF_UP).movePointRight(4).toLong())
}

/**
 * Parses AMFI's NAVAll.txt: semicolon-separated `Scheme Code; ISIN Div Payout/ ISIN Growth; ISIN Div Reinvestment;
 * Scheme Name; Plan; Option; Net Asset Value; Date`, with fund-house and category lines in between that have no
 * semicolons. Anything that isn't a scheme line (by a numeric scheme code) is skipped.
 */
fun parseAmfi(text: String): List<AmfiRow> = text.lineSequence().mapNotNull { line ->
    val cells = line.split(';')
    if (cells.size < 8 || cells[0].trim().toLongOrNull() == null) return@mapNotNull null
    val date = try { LocalDate.parse(cells[7].trim(), AMFI_DATE) } catch (_: DateTimeParseException) { null }
    AmfiRow(
        schemeCode = cells[0].trim(),
        isins = listOf(cells[1], cells[2]).map { it.trim() }.filter { it.length == 12 && it != "-" },
        name = cells[3].trim(),
        nav = parsePriceText(cells[6]),
        date = date,
    )
}.toList()

/** One line of NSE's end-of-day (UDiFF) cash-market file. */
data class NseRow(val isin: String, val symbol: String, val series: String, val name: String, val close: UnitPrice, val date: LocalDate)

/**
 * Parses NSE's `BhavCopy_NSE_CM_0_0_0_<yyyyMMdd>_F_0000.csv`. Columns are found by header name (`TradDt`, `ISIN`, `TckrSymb`,
 * `SctySrs`, `FinInstrmNm`, `ClsPric`), so a reordered or widened file still parses; rows missing any of them are skipped.
 */
fun parseNse(csv: String): List<NseRow> {
    val lines = csv.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()
    val header = lines.first().split(',').map { it.trim() }
    val col = header.withIndex().associate { (i, name) -> name to i }
    val needed = listOf("TradDt", "ISIN", "TckrSymb", "SctySrs", "ClsPric")
    if (needed.any { it !in col }) return emptyList()
    return lines.drop(1).mapNotNull { line ->
        val cells = line.split(',')
        fun cell(name: String) = col[name]?.let { cells.getOrNull(it) }?.trim().orEmpty()
        val close = parsePriceText(cell("ClsPric")) ?: return@mapNotNull null
        val date = try { LocalDate.parse(cell("TradDt")) } catch (_: DateTimeParseException) { return@mapNotNull null }
        if (cell("ISIN").isEmpty() || cell("TckrSymb").isEmpty()) return@mapNotNull null
        NseRow(cell("ISIN"), cell("TckrSymb"), cell("SctySrs"), cell("FinInstrmNm"), close, date)
    }
}

/** The text of the first file in a zip archive, or null if the bytes aren't a zip. */
fun unzipFirst(bytes: ByteArray): String? = try {
    ZipInputStream(bytes.inputStream()).use { zip -> zip.nextEntry?.let { zip.readBytes().toString(Charsets.UTF_8) } }
} catch (_: java.io.IOException) {
    null
}

/** NSE trades the same ISIN in several series; plain equity (EQ) is the price to use, then the others in this order. */
private val SERIES_ORDER = listOf("EQ", "BE", "BZ", "SM", "ST")

/** The best row per ISIN, and per symbol, preferring [SERIES_ORDER]. */
class NseIndex(rows: List<NseRow>) {
    private fun rank(row: NseRow) = SERIES_ORDER.indexOf(row.series).let { if (it < 0) SERIES_ORDER.size else it }
    private val byIsin = rows.groupBy { it.isin }.mapValues { (_, v) -> v.minBy(::rank) }
    private val bySymbol = rows.filter { it.series in SERIES_ORDER }.groupBy { it.symbol.uppercase() }.mapValues { (_, v) -> v.minBy(::rank) }

    fun byIsin(isin: String): NseRow? = byIsin[isin]
    fun bySymbol(symbol: String): NseRow? = bySymbol[symbol.uppercase()]
}
