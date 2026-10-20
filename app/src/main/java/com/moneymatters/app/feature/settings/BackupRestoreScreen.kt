package com.moneymatters.app.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.theme.MoneyTheme
import kotlinx.coroutines.launch

/** Backup, restore and CSV export (plan.html → P21), over the already SQLCipher-encrypted database file. */
@Composable
fun BackupRestoreScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Backing up…"
            status = runCatching { viewModel.backup(context, uri) }.fold({ "Backed up." }, { "Couldn't back up: ${it.message}" })
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Exporting…"
            status = runCatching {
                val csv = viewModel.exportCsv()
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } ?: error("Couldn't open $uri")
            }.fold({ "Exported." }, { "Couldn't export: ${it.message}" })
        }
    }

    val margin = MoneyTheme.spacing.margin
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = statusBarPadding.calculateTopPadding())) {
            DetailTopBar(title = "Backup & restore", onBack = onBack)
        }
        LazyColumn(contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp)) {
            item("backup") {
                Module {
                    ModuleHeader("Backup", subtitle = "A copy of the encrypted database file")
                    Pill("Save a backup", onClick = { backupLauncher.launch("money-matters-backup.db") }, style = PillStyle.Primary)
                }
            }
            item("restore") {
                Module(modifier = Modifier.padding(top = MoneyTheme.spacing.gutter)) {
                    ModuleHeader(
                        "Restore",
                        subtitle = "Only works with a backup from this device — the decryption key lives in this device's secure storage",
                    )
                    Pill("Choose a backup file", onClick = { restoreLauncher.launch(arrayOf("*/*")) }, style = PillStyle.Destructive)
                }
            }
            item("export") {
                Module(modifier = Modifier.padding(top = MoneyTheme.spacing.gutter)) {
                    ModuleHeader("Export", subtitle = "A CSV of every posting")
                    Pill("Export as CSV", onClick = { exportLauncher.launch("money-matters-export.csv") })
                }
            }
            if (status != null) {
                item("status") {
                    Text(status!!, style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }

    restoreUri?.let { uri -> RestoreConfirmDialog(onDismiss = { restoreUri = null }, onConfirm = { restoreUri = null; scope.launch { restoreDatabase(context, uri) } }) }
}

@Composable
private fun RestoreConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace the current ledger?") },
        text = {
            Column {
                Text("This overwrites every account and transaction with the chosen backup, then restarts the app. Type RESTORE to confirm.")
                FormField("Confirm", typed, { typed = it }, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed == "RESTORE") { Text("Restore and restart") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
