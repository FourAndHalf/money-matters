package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.parseRupees
import com.moneymatters.app.core.money.parseUnits
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * What a trade-history row does. Splits and bonuses come first so that, on the day they take effect,
 * the day's trades are read as being in the new terms. For those two, `units` holds the ratio (new units per old
 * for a split, bonus units per unit held for a bonus) and `amount` is unused.
 */
enum class TradeSide { Split, Bonus, Buy, Sell }

/** One line of a broker's trade history: [amount] is the net rupee figure paid or received, after every charge (zero for a split or bonus). */
data class TradeRow(
    val line: Int,
    val date: LocalDate,
    val symbol: String,
    val isin: String?,
    val name: String?,
    val side: TradeSide,
    val units: Units,
    val amount: Paise,
    val instrument: Instrument = Instrument.Stock,
    val fundClass: FundClass? = null,
)

data class RowError(val line: Int, val message: String)

data class TradeParse(val rows: List<TradeRow>, val errors: List<RowError>)

private val DATE_FORMATS = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE,
    DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
)
private val REQUIRED = listOf("date", "symbol", "type", "units", "amount")

/**
 * Parses a trade-history CSV: a header row with `date, symbol, type, units, amount` (any order,
 * any case) and optionally `isin, name`; then one trade per row. `type` is buy or sell; dates are
 * `yyyy-MM-dd`, `dd-MM-yyyy` or `dd/MM/yyyy`; fields may be double-quoted. Every bad row is reported
 * with its line number rather than dropping silently, and nothing is imported if any row is bad.
 */
fun parseTrades(csv: String): TradeParse {
    val lines = csv.lines().mapIndexed { i, text -> i + 1 to text }.filter { it.second.isNotBlank() }
    if (lines.isEmpty()) return TradeParse(emptyList(), listOf(RowError(1, "The file is empty")))
    val header = splitCsv(lines.first().second).map { it.trim().lowercase() }
    val missing = REQUIRED.filter { it !in header }
    if (missing.isNotEmpty()) return TradeParse(emptyList(), listOf(RowError(lines.first().first, "Missing column(s): ${missing.joinToString()}")))
    val col = header.withIndex().associate { (i, name) -> name to i }

    val rows = mutableListOf<TradeRow>()
    val errors = mutableListOf<RowError>()
    for ((lineNo, text) in lines.drop(1)) {
        val cells = splitCsv(text).map { it.trim() }
        fun cell(name: String) = col[name]?.let { cells.getOrNull(it) }?.takeIf { it.isNotEmpty() }
        val date = cell("date")?.let(::parseDate)
        val symbol = cell("symbol")?.uppercase()
        val side = when (cell("type")?.lowercase()) {
            "buy", "b" -> TradeSide.Buy
            "sell", "s" -> TradeSide.Sell
            "split" -> TradeSide.Split
            "bonus" -> TradeSide.Bonus
            else -> null
        }
        val instrument = when (cell("instrument")?.lowercase()) {
            null, "stock", "share", "equity" -> Instrument.Stock
            "mf", "fund", "mutual fund", "mutualfund" -> Instrument.MutualFund
            else -> null
        }
        val fundClass = cell("class")?.let { text -> parseFundClass(text) }
        val corporateAction = side == TradeSide.Split || side == TradeSide.Bonus
        val units = cell("units")?.let(::parseUnits)?.takeIf { it.micro > 0 }
        val amount = if (corporateAction) Paise.Zero else cell("amount")?.let(::parseRupees)?.takeIf { it.value > 0 }
        val problem = when {
            date == null -> "Date isn't valid (use yyyy-MM-dd or dd-MM-yyyy)"
            instrument == null -> "Instrument must be stock or mf"
            cell("class") != null && fundClass == null -> "Class must be equity, equity-hybrid, hybrid, debt or elss"
            symbol == null -> "Symbol is missing"
            side == null -> "Type must be buy, sell, split or bonus"
            units == null -> "Units (or the split/bonus ratio) must be a positive number with at most 6 decimals"
            amount == null -> "Amount must be a positive rupee figure with at most 2 decimals"
            else -> null
        }
        if (problem != null) errors += RowError(lineNo, problem)
        else rows += TradeRow(lineNo, date!!, symbol!!, cell("isin")?.uppercase(), cell("name"), side!!, units!!, amount!!, instrument!!, fundClass)
    }
    return TradeParse(rows, errors)
}

private fun parseFundClass(text: String): FundClass? = when (text.lowercase().replace(" ", "-")) {
    "equity" -> FundClass.Equity
    "equity-hybrid", "equityhybrid" -> FundClass.EquityHybrid
    "hybrid" -> FundClass.Hybrid
    "debt" -> FundClass.Debt
    "elss" -> FundClass.Elss
    else -> null
}

private fun parseDate(text: String): LocalDate? = DATE_FORMATS.firstNotNullOfOrNull {
    try { LocalDate.parse(text, it) } catch (_: DateTimeParseException) { null }
}

private fun splitCsv(line: String): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            quoted && ch == '"' && line.getOrNull(i + 1) == '"' -> { current.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> { cells += current.toString(); current.clear() }
            else -> current.append(ch)
        }
        i++
    }
    cells += current.toString()
    return cells
}

/** Buys before sells on the same day, so a same-day round trip never looks like a short sale. */
fun List<TradeRow>.inLedgerOrder(): List<TradeRow> = sortedWith(compareBy({ it.date }, { it.side.ordinal }, { it.line }))

/** What a symbol will hold after the import. */
data class SymbolPreview(val symbol: String, val name: String?, val trades: Int, val unitsHeld: Units, val costHeld: Paise)

data class TradePreview(val symbols: List<SymbolPreview>, val errors: List<RowError>)

/**
 * Replays [rows] in date order against each symbol's FIFO lots, starting from [existing] (open
 * lots already in the books, keyed by upper-case symbol). Reports any sale of more units than were
 * held on that date, which is what would make the import refuse, without touching the database.
 */
fun previewTrades(rows: List<TradeRow>, existing: Map<String, List<OpenLot>> = emptyMap()): TradePreview {
    val lots = existing.mapValues { it.value.toMutableList() }.toMutableMap()
    val tradeCounts = HashMap<String, Int>()
    val names = HashMap<String, String?>()
    val errors = mutableListOf<RowError>()
    for (row in rows.inLedgerOrder()) {
        val symbolLots = lots.getOrPut(row.symbol) { mutableListOf() }
        tradeCounts[row.symbol] = (tradeCounts[row.symbol] ?: 0) + 1
        if (row.name != null) names[row.symbol] = row.name
        when (row.side) {
            TradeSide.Split, TradeSide.Bonus -> {
                val kind = if (row.side == TradeSide.Split) RestateKind.Split else RestateKind.Bonus
                try {
                    val plan = restate(symbolLots, kind, row.units, row.date)
                    symbolLots.removeAll { lot -> plan.consumed.any { it.lotId == lot.id } }
                    plan.created.forEachIndexed { i, lot -> symbolLots += OpenLot("import-${row.line}-$i", lot.acquiredOn, lot.units, lot.cost) }
                } catch (_: IllegalArgumentException) {
                    errors += RowError(row.line, "${row.symbol} has nothing held on ${row.date} for a ${kind.name.lowercase()}")
                }
            }
            TradeSide.Buy -> symbolLots += OpenLot("import-${row.line}", row.date, row.units, row.amount)
            TradeSide.Sell -> {
                val eligible = symbolLots.filter { !it.acquiredOn.isAfter(row.date) }
                val held = eligible.fold(Units.Zero) { acc, lot -> acc + lot.remainingUnits }
                if (held < row.units) {
                    errors += RowError(row.line, "Sells ${formatUnits(row.units)} ${row.symbol} on ${row.date} but only ${formatUnits(held)} were held")
                    continue
                }
                for (used in allocateFifo(eligible, row.units)) {
                    val i = symbolLots.indexOfFirst { it.id == used.lotId }
                    val lot = symbolLots[i]
                    symbolLots[i] = lot.copy(remainingUnits = lot.remainingUnits - used.units, remainingCost = lot.remainingCost - used.cost)
                }
            }
        }
    }
    val symbols = lots.keys.filter { tradeCounts.containsKey(it) }.sorted().map { symbol ->
        val open = lots.getValue(symbol)
        SymbolPreview(
            symbol, names[symbol], tradeCounts.getValue(symbol),
            open.fold(Units.Zero) { acc, l -> acc + l.remainingUnits },
            Paise(open.sumOf { it.remainingCost.value }),
        )
    }
    return TradePreview(symbols, errors)
}

/** "12.5" for 12,500,000 micro-units: trailing zeros trimmed, whole numbers have no point. */
fun formatUnits(units: Units): String {
    val whole = units.micro / 1_000_000
    val fraction = (units.micro % 1_000_000).toString().padStart(6, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}
