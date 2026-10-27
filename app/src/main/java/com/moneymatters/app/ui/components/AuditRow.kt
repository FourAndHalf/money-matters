package com.moneymatters.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.theme.MoneyTheme

/**
 * One change in an audit log: when, what kind, which entry and field, and, when a value changed, the
 * old and new value on their own lines so long values wrap instead of squeezing a one-line arrow.
 * [title] names the entry (the global log needs it; an entry's own history doesn't).
 */
@Composable
fun AuditRow(
    whenLabel: String,
    summary: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    title: String? = null,
    before: String? = null,
    after: String? = null,
) {
    val c = MoneyTheme.colors
    Column(modifier.padding(vertical = 10.dp)) {
        Text(
            listOfNotNull(whenLabel, action).joinToString(" · "),
            style = MoneyTheme.type.bodySm,
            color = c.muted,
        )
        if (title != null) Text(title, style = MoneyTheme.type.headlineMd, color = c.ink, modifier = Modifier.padding(top = 3.dp))
        Text(summary, style = MoneyTheme.type.bodyMd, color = if (title != null) c.muted else c.ink, modifier = Modifier.padding(top = 2.dp))
        if (before != null && after != null) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ValueLine("Was", before, c.muted)
                ValueLine("Now", after, c.ink)
            }
        }
    }
}

@Composable
private fun ValueLine(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.width(30.dp))
        Text(value, style = MoneyTheme.type.ledgerMono, color = color)
    }
}
