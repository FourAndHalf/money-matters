package com.moneymatters.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.profile.TaxRegime
import com.moneymatters.app.core.security.LockTimeout
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.Toggle
import com.moneymatters.app.ui.theme.MoneyTheme

private enum class SettingsSection { Profile, Security, Accounts, Ai, Backup, AuditLog }

/** Settings (P21): everything the app's other pages read from or write to, and nothing it doesn't. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onClose: () -> Unit, modifier: Modifier = Modifier, onOpenClose: () -> Unit = {}) {
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }

    section?.let {
        BackHandler { section = null }
        when (it) {
            SettingsSection.Profile -> ProfileSettingsScreen(viewModel, onBack = { section = null })
            SettingsSection.Security -> SecuritySettingsScreen(viewModel, onBack = { section = null })
            SettingsSection.Accounts -> AccountsEditorScreen(viewModel, onBack = { section = null })
            SettingsSection.Ai -> AiSettingsScreen(viewModel, onBack = { section = null })
            SettingsSection.Backup -> BackupRestoreScreen(viewModel, onBack = { section = null })
            SettingsSection.AuditLog -> AuditLogScreen(viewModel, onBack = { section = null })
        }
        return
    }

    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = "Settings", onBack = onClose)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("profile") {
                Module {
                    SettingsRow("Profile", "Date of birth, month start day, tax regime") { section = SettingsSection.Profile }
                    SettingsRow("Security", "Passcode, biometric, timeout", divider = false) { section = SettingsSection.Security }
                }
            }
            item("accounts") {
                Module {
                    SettingsRow("Accounts & categories", "Add, rename, archive, delete, mark essentials", divider = false) { section = SettingsSection.Accounts }
                }
            }
            item("display") {
                Module {
                    ModuleHeader("Display")
                    ToggleRow("Accountant mode", "Dr/Cr columns on transaction rows", viewModel.prefs.accountantMode) { viewModel.prefs.accountantMode = it }
                    ToggleRow(
                        "Compact numbers",
                        "₹2.4L instead of ₹2,40,000",
                        viewModel.prefs.numberFormat == NumberFormat.Compact,
                        divider = false,
                    ) { viewModel.prefs.numberFormat = if (it) NumberFormat.Compact else NumberFormat.Full }
                }
            }
            item("accountability") {
                Module {
                    ModuleHeader("Accountability", subtitle = "Not active until the impact prompt ships (Phase 3)")
                    ToggleRow("Decision-tag prompts", "Ask need/want and planned/unplanned on every entry", viewModel.prefs.decisionTagPrompts, divider = false) {
                        viewModel.prefs.decisionTagPrompts = it
                    }
                }
            }
            item("runway-prices") {
                Module {
                    SettingsRow("Runway", "Stress drops and liquidity tiers — coming in Phase 3", clickable = false)
                    SettingsRow("Prices", "Sources, URLs and the weekly schedule — coming in Phase 2", divider = false, clickable = false)
                }
            }
            item("ai") {
                Module {
                    SettingsRow("AI", "On/off, API key, payload preview — not active until the weekly review ships (Phase 4)", divider = false) {
                        section = SettingsSection.Ai
                    }
                }
            }
            item("notifications") {
                Module {
                    ToggleRow(
                        "Notifications",
                        "Not active until bill, EMI and SIP reminders ship (Phase 2)",
                        viewModel.prefs.notificationsEnabled,
                        divider = false,
                    ) { viewModel.prefs.notificationsEnabled = it }
                }
            }
            item("backup") {
                Module {
                    SettingsRow("Backup & restore, export", "Encrypted database file, or a CSV of every posting", divider = false) {
                        section = SettingsSection.Backup
                    }
                }
            }
            item("close") {
                Module {
                    SettingsRow("Month-end close", "Reconcile, confirm balances and lock a finished month", divider = false, onClick = onOpenClose)
                }
            }
            item("audit") {
                Module {
                    SettingsRow("Audit log", "Every ledger change, before and after", divider = false) { section = SettingsSection.AuditLog }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, divider: Boolean = true, clickable: Boolean = true, onClick: () -> Unit = {}) {
    ListRow(
        title = title,
        subtitle = subtitle,
        divider = divider,
        modifier = if (clickable) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, divider: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    ListRow(
        title = title,
        subtitle = subtitle,
        divider = divider,
        trailing = { Toggle(checked, onCheckedChange) },
    )
}

@Composable
private fun ProfileSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val form = viewModel.profileForm
    SettingsSubScreen("Profile", onBack) {
        Module {
            FormField(
                "Month start day",
                form.monthStartDay.toString(),
                onValueChange = { text -> text.toIntOrNull()?.takeIf { it in 1..28 }?.let { viewModel.profileForm = form.copy(monthStartDay = it) } },
                keyboardType = KeyboardType.Number,
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaxRegime.entries.forEach { regime ->
                    Pill(
                        regime.name,
                        onClick = { viewModel.profileForm = form.copy(taxRegime = regime) },
                        style = if (form.taxRegime == regime) PillStyle.Primary else PillStyle.Secondary,
                    )
                }
            }
            Pill(
                if (viewModel.isSavingProfile) "Saving…" else "Save",
                onClick = viewModel::saveProfile,
                style = PillStyle.Primary,
                modifier = Modifier.padding(top = 14.dp),
            )
            if (viewModel.profileSaveFailed) {
                Text("Couldn't save — try again.", color = MoneyTheme.colors.negative, style = MoneyTheme.type.bodySm, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun SecuritySettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    SettingsSubScreen("Security", onBack) {
        Module {
            ModuleHeader("App lock")
            ToggleRow("Ask for passcode on open", "Off opens the app straight to your books", viewModel.lockEnabled, divider = viewModel.lockEnabled) {
                viewModel.updateLockEnabled(it)
            }
            if (viewModel.lockEnabled) {
                ToggleRow("Use biometrics", "Fingerprint or face, alongside the passcode", viewModel.biometricEnabled, divider = false) {
                    viewModel.updateBiometricEnabled(it)
                }
            }
        }
        if (!viewModel.lockEnabled) return@SettingsSubScreen
        Module {
            ModuleHeader("Lock timeout")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LockTimeout.entries.forEach { timeout ->
                    Pill(
                        timeout.name,
                        onClick = { viewModel.updateLockTimeout(timeout) },
                        style = if (viewModel.lockTimeout == timeout) PillStyle.Primary else PillStyle.Secondary,
                    )
                }
            }
        }
        Module {
            ModuleHeader("Change passcode")
            FormField("Current passcode", viewModel.currentPasscode, { viewModel.currentPasscode = it }, keyboardType = KeyboardType.NumberPassword)
            FormField("New passcode", viewModel.newPasscode, { viewModel.newPasscode = it }, modifier = Modifier.padding(top = 10.dp), keyboardType = KeyboardType.NumberPassword)
            FormField("Confirm new passcode", viewModel.confirmPasscode, { viewModel.confirmPasscode = it }, modifier = Modifier.padding(top = 10.dp), keyboardType = KeyboardType.NumberPassword)
            Pill("Change passcode", onClick = viewModel::changePasscode, style = PillStyle.Primary, modifier = Modifier.padding(top = 14.dp))
            if (viewModel.passcodeChangeFailed) {
                Text("Current passcode is wrong, or the new one doesn't match.", color = MoneyTheme.colors.negative, style = MoneyTheme.type.bodySm, modifier = Modifier.padding(top = 8.dp))
            }
            if (viewModel.passcodeChanged) {
                Text("Passcode changed.", color = MoneyTheme.colors.positive, style = MoneyTheme.type.bodySm, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun AiSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(viewModel.ai.enabled) }
    var apiKey by remember { mutableStateOf(viewModel.ai.apiKey.orEmpty()) }
    var payloadPreview by remember { mutableStateOf(viewModel.ai.payloadPreview) }
    SettingsSubScreen("AI", onBack) {
        Module {
            ModuleHeader("AI", subtitle = "Not active until the weekly review (P19, Phase 4) ships")
            ToggleRow("Enabled", "Turn on once the weekly review exists", enabled) { enabled = it; viewModel.ai.enabled = it }
            FormField(
                "API key",
                apiKey,
                onValueChange = { apiKey = it; viewModel.ai.apiKey = it },
                modifier = Modifier.padding(top = 10.dp),
                keyboardType = KeyboardType.Password,
            )
            ToggleRow("Show payload preview", "See exactly what would be sent before each call", payloadPreview, divider = false) {
                payloadPreview = it
                viewModel.ai.payloadPreview = it
            }
        }
    }
}

@Composable
private fun SettingsSubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = title, onBack = onBack)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("content") {
                Column(verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter)) { content() }
            }
        }
    }
}
