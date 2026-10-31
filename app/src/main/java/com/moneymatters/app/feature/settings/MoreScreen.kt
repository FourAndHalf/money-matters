package com.moneymatters.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.feature.recurring.RecurringScreen
import com.moneymatters.app.feature.recurring.RecurringViewModel
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.theme.MoneyTheme

private enum class MoreSection { Backup, AuditLog, Recurring }

/** The More tab: things you do with your books (close a month, back up, inspect the log), as opposed to Settings' preferences. */
@Composable
fun MoreScreen(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    onOpenClose: () -> Unit,
    modifier: Modifier = Modifier,
    recurring: RecurringViewModel? = null,
) {
    var section by rememberSaveable { mutableStateOf<MoreSection?>(null) }

    section?.let {
        BackHandler { section = null }
        when (it) {
            MoreSection.Backup -> BackupRestoreScreen(viewModel, onBack = { section = null })
            MoreSection.AuditLog -> AuditLogScreen(viewModel, onBack = { section = null })
            MoreSection.Recurring -> if (recurring != null) RecurringScreen(recurring, onBack = { section = null }) else section = null
        }
        return
    }

    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = "More", onBack = onClose)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("close") {
                Module {
                    SettingsRow("Month-end close", "Reconcile, confirm balances and lock a finished month", divider = false, onClick = onOpenClose)
                }
            }
            if (recurring != null) item("recurring") {
                Module {
                    SettingsRow("Recurring entries", "Salary, rent, EMIs and subscriptions, booked on their dates", divider = false) { section = MoreSection.Recurring }
                }
            }
            item("backup") {
                Module {
                    SettingsRow("Backup & restore, export", "Encrypted database file, or a CSV of every posting", divider = false) {
                        section = MoreSection.Backup
                    }
                }
            }
            item("audit") {
                Module {
                    SettingsRow("Audit log", "Every ledger change, before and after", divider = false) { section = MoreSection.AuditLog }
                }
            }
        }
    }
}
