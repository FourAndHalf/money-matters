package com.moneymatters.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** What a day on a [MonthCalendar] stands for. Each also reads out as words, so colour is never the only signal. */
enum class CalendarMark(val word: String) {
    Done("done"),
    Due("due"),
    Missed("missed"),
    Skipped("skipped"),
    Upcoming("upcoming"),
}

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** One month, weeks starting Monday, with [marks] (by date) drawn as filled or ringed days and [today] underlined. */
@Composable
fun MonthCalendar(
    month: YearMonth,
    marks: Map<LocalDate, CalendarMark>,
    today: LocalDate,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
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
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) DayCell(month.atDay(day), marks[month.atDay(day)], month.atDay(day) == today)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, mark: CalendarMark?, isToday: Boolean) {
    val c = MoneyTheme.colors
    val (fill, ink) = when (mark) {
        CalendarMark.Done -> c.positiveContainer to c.positive
        CalendarMark.Due -> c.warningContainer to c.warning
        CalendarMark.Missed -> c.negativeContainer to c.negative
        CalendarMark.Skipped -> c.outline to c.muted
        CalendarMark.Upcoming, null -> Color.Transparent to if (mark == null) c.muted else c.ink
    }
    var cell = Modifier.size(32.dp).clip(CircleShape).background(fill, CircleShape)
    if (mark == CalendarMark.Upcoming) cell = cell.border(1.5.dp, c.anchor, CircleShape)
    else if (isToday) cell = cell.border(1.dp, c.ink.copy(alpha = 0.5f), CircleShape)
    val label = date.dayOfMonth.toString() + (mark?.let { ", ${it.word}" } ?: "") + if (isToday) ", today" else ""
    Box(cell.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Text(date.dayOfMonth.toString(), style = MoneyTheme.type.bodySm, color = ink)
    }
}

/** A small key for the marks a calendar actually uses. */
@Composable
fun CalendarLegend(marks: Collection<CalendarMark>, modifier: Modifier = Modifier) {
    val c = MoneyTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CalendarMark.entries.filter { it in marks }.forEach { m ->
            val color = when (m) {
                CalendarMark.Done -> c.positive
                CalendarMark.Due -> c.warning
                CalendarMark.Missed -> c.negative
                CalendarMark.Skipped -> c.muted
                CalendarMark.Upcoming -> c.anchor
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(m.word.replaceFirstChar(Char::uppercase), style = MoneyTheme.type.bodySm, color = c.muted)
            }
        }
    }
}
