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
import com.moneymatters.app.feature.budgets.BudgetsScreen
import com.moneymatters.app.feature.budgets.BudgetsViewModel
import com.moneymatters.app.feature.recurring.RecurringScreen
import com.moneymatters.app.feature.reports.ReportsScreen
import com.moneymatters.app.feature.reports.ReportsViewModel
import com.moneymatters.app.feature.recurring.RecurringViewModel
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.theme.MoneyTheme

private enum class MoreSection { Backup, AuditLog, Recurring, Budgets, Reports }

/** The More tab: things you do with your books (close a month, back up, inspect the log), as opposed to Settings' preferences. */
@Composable
fun MoreScreen(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    onOpenClose: () -> Unit,
    modifier: Modifier = Modifier,
    recurring: RecurringViewModel? = null,
    budgets: BudgetsViewModel? = null,
    reports: ReportsViewModel? = null,
) {
    var section by rememberSaveable { mutableStateOf<MoreSection?>(null) }

    section?.let {
        BackHandler { section = null }
        when (it) {
            MoreSection.Backup -> BackupRestoreScreen(viewModel, onBack = { section = null })
            MoreSection.AuditLog -> AuditLogScreen(viewModel, onBack = { section = null })
            MoreSection.Reports -> if (reports != null) ReportsScreen(reports, onBack = { section = null }) else section = null
            MoreSection.Budgets -> if (budgets != null) BudgetsScreen(budgets, onBack = { section = null }) else section = null
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
            if (reports != null) item("reports") {
                Module {
                    SettingsRow("Reports", "Income statement, balance sheet and trial balance", divider = false) { section = MoreSection.Reports }
                }
            }
            if (budgets != null) item("budgets") {
                Module {
                    SettingsRow("Budgets", "Spending against each category's budget, with pace", divider = false) { section = MoreSection.Budgets }
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
