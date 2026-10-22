package com.moneymatters.app.feature.invest

import androidx.compose.runtime.Immutable
import com.moneymatters.app.core.invest.HoldingValuation
import com.moneymatters.app.core.invest.InvestSnapshot
import com.moneymatters.app.core.invest.PriceFetchLogEntity
import com.moneymatters.app.core.invest.PriceSourceId
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.effectiveStatus
import com.moneymatters.app.ui.components.Tone
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DayMonth = DateTimeFormatter.ofPattern("d MMM")
private val DayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")
private const val LOG_ROWS = 10

/** How a price source is doing, in one line and the reason behind it (plan.html → P15's status bar). */
@Immutable
data class SourceStatus(val id: PriceSourceId, val name: String, val tone: Tone, val headline: String, val detail: String?)

/** One held stock or fund with its latest price. [priceId] is that price's row, for confirming or resolving it. */
@Immutable
data class PriceRow(
    val holdingId: String,
    val name: String,
    val priceLabel: String?,
    val detailLabel: String?,
    val tag: PriceTag,
    val priceId: Long?,
    val dateIso: String?,
    val status: PriceStatus?,
    /** Not Verified or confirmed: needs a look before month-end close can use it. */
    val needsAttention: Boolean,
)

@Immutable
data class LogRow(val whenLabel: String, val source: String, val ok: Boolean, val message: String)

@Immutable
data class PricesState(
    val sources: List<SourceStatus> = emptyList(),
    val rows: List<PriceRow> = emptyList(),
    val log: List<LogRow> = emptyList(),
) {
    val attentionCount: Int get() = rows.count { it.needsAttention }
}

internal fun buildPrices(valued: List<HoldingValuation>, rowsById: Map<String, HoldingRow>, invest: InvestSnapshot, today: LocalDate, zone: ZoneId): PricesState {
    val rows = valued.filter { it.units.micro > 0 }.map { v ->
        val price = v.price
        val status = price?.effectiveStatus(today)
        PriceRow(
            holdingId = v.holding.id,
            name = rowsById.getValue(v.holding.id).name,
            priceLabel = price?.let { formatUnitPrice(it.price) },
            detailLabel = price?.let { "${LocalDate.parse(it.date).format(DayMonthYear)} · ${it.source}" },
            tag = status?.tag() ?: PriceTag("No price", Tone.Neutral),
            priceId = price?.id,
            dateIso = price?.date,
            status = status,
            needsAttention = status == null || (status != PriceStatus.Verified && status != PriceStatus.Manual),
        )
    }.sortedWith(compareByDescending<PriceRow> { it.needsAttention }.thenBy { it.name.lowercase() })
    return PricesState(
        sources = PriceSourceId.entries.map { sourceStatus(it, invest.fetchLog, zone) },
        rows = rows,
        log = invest.fetchLog.sortedWith(compareByDescending<PriceFetchLogEntity> { it.attemptedAt }.thenByDescending { it.id }).take(LOG_ROWS).map {
            LogRow(Instant.ofEpochMilli(it.attemptedAt).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm")), PriceSourceId.entries.first { s -> s.key == it.source }.label, it.ok, it.message)
        },
    )
}

private fun sourceStatus(id: PriceSourceId, log: List<PriceFetchLogEntity>, zone: ZoneId): SourceStatus {
    fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(DayMonth)
    val entries = log.filter { it.source == id.key }.sortedWith(compareBy({ it.attemptedAt }, { it.id }))
    val last = entries.lastOrNull() ?: return SourceStatus(id, id.label, Tone.Neutral, "Not downloaded yet", null)
    if (last.ok) return SourceStatus(id, id.label, Tone.Positive, "✓ Updated ${day(last.attemptedAt)}", last.message)
    val failingSince = entries.takeLastWhile { !it.ok }.first()
    val lastSuccess = entries.lastOrNull { it.ok }
    return SourceStatus(
        id, id.label, Tone.Negative, "✕ Failing since ${day(failingSince.attemptedAt)}",
        (lastSuccess?.let { "Last success ${day(it.attemptedAt)} · " } ?: "Never succeeded · ") + last.message,
    )
}
