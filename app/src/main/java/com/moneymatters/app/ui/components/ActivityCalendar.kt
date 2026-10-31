package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.core.money.formatInrCompact
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/**
 * One month with each day's net money movement under its date (out in the negative colour, in in the positive),
 * weeks starting Monday. [nets] holds only days that had entries; tapping a day calls [onSelect].
 */
@Composable
fun ActivityCalendar(
    month: YearMonth,
    nets: Map<LocalDate, Paise>,
    selected: LocalDate?,
    today: LocalDate,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MoneyTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (canPrev) Pill("‹", onPrev, height = 28.dp, modifier = Modifier.semantics { contentDescription = "Previous month" }) else Spacer(Modifier.width(36.dp))
            Text(month.format(MonthTitle), style = MoneyTheme.type.labelUppercase, color = c.ink)
            if (canNext) Pill("›", onNext, height = 28.dp, modifier = Modifier.semantics { contentDescription = "Next month" }) else Spacer(Modifier.width(36.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
            for (i in 0 until 7) {
                Text(
                    DayOfWeek.MONDAY.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = MoneyTheme.type.bodySm, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        val lead = month.atDay(1).dayOfWeek.value - 1
        val weeks = (lead + month.lengthOfMonth() + 6) / 7
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val day = w * 7 + d - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(0.85f).padding(1.dp), contentAlignment = Alignment.TopCenter) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            ActivityDay(date, nets[date], date == selected, date == today, onSelect)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityDay(date: LocalDate, net: Paise?, isSelected: Boolean, isToday: Boolean, onSelect: (LocalDate) -> Unit) {
    val c = MoneyTheme.colors
    val label = buildString {
        append(date.dayOfMonth)
        if (net != null) append(", net ${net.formatInr()}")
        if (isToday) append(", today")
        if (isSelected) append(", selected")
    }
    var cell = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
    cell = if (isSelected) cell.background(c.surfaceLowest).border(1.dp, c.anchor, RoundedCornerShape(8.dp))
    else if (isToday) cell.border(1.dp, c.ink.copy(alpha = 0.4f), RoundedCornerShape(8.dp)) else cell
    Column(
        cell.clickable { onSelect(date) }.padding(top = 4.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(date.dayOfMonth.toString(), style = MoneyTheme.type.bodySm, color = if (net == null) c.muted else c.ink)
        if (net != null && net.value != 0L) {
            Text(
                net.formatInrCompact(), style = MoneyTheme.type.bodySm, color = if (net.value < 0) c.negative else c.positive,
                maxLines = 1, softWrap = false,
            )
        }
    }
}
