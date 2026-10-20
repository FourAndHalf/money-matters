package com.moneymatters.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.ledger.AuditLogEntity
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.RowDivider
import com.moneymatters.app.ui.theme.MoneyTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WHEN_LABEL = DateTimeFormatter.ofPattern("d MMM · HH:mm")

/** Every ledger mutation, append-only and newest first (plan.html → P21's "Audit log viewer"). */
@Composable
fun AuditLogScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsState()
    val entries = snapshot?.audit.orEmpty().sortedByDescending { it.whenMillis }

    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = "Audit log", subtitle = "${entries.size} entries", onBack = onBack)
        }
        LazyColumn(contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp)) {
            item("entries") {
                Module {
                    ModuleHeader("Every change, before and after")
                    if (entries.isEmpty()) {
                        Text("Nothing yet.", style = MoneyTheme.type.bodyMd, color = MoneyTheme.colors.muted)
                    }
                    entries.forEachIndexed { i, entry ->
                        AuditRow(entry)
                        if (i < entries.lastIndex) RowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditRow(entry: AuditLogEntity) {
    val c = MoneyTheme.colors
    val whenLabel = Instant.ofEpochMilli(entry.whenMillis).atZone(ZoneId.systemDefault()).format(WHEN_LABEL)
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(whenLabel, style = MoneyTheme.type.labelUppercase, color = c.muted)
        Text("${entry.action} — ${entry.summary}", style = MoneyTheme.type.bodyMd, color = c.ink, modifier = Modifier.padding(top = 2.dp))
        if (entry.before != null && entry.after != null) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.before, style = MoneyTheme.type.ledgerMono, color = c.muted)
                Text("→", style = MoneyTheme.type.ledgerMono, color = c.muted)
                Text(entry.after, style = MoneyTheme.type.ledgerMono, color = c.ink)
            }
        }
    }
}
