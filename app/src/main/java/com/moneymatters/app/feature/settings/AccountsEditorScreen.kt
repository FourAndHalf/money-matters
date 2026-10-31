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
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountGoal
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LiquidityTier
import com.moneymatters.app.core.ledger.effectiveTier
import com.moneymatters.app.core.ledger.label
import com.moneymatters.app.core.ledger.moneyMap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.moneymatters.app.core.money.formatInr
import com.moneymatters.app.ui.components.KeyValueRow
import com.moneymatters.app.feature.books.displayName
import com.moneymatters.app.ui.components.ConfirmDialog
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
import com.moneymatters.app.ui.components.RowDivider
import com.moneymatters.app.ui.components.Toggle
import com.moneymatters.app.ui.theme.MoneyTheme

/** Account/category tree editor and essential flags (plan.html → P21). */
@Composable
fun AccountsEditorScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsState()
    val accounts = snapshot?.accounts.orEmpty().sortedBy { it.name.lowercase() }
    val essentials = viewModel.profileForm.essentialCategories

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newType by rememberSaveable { mutableStateOf(AccountType.Expense) }
    var addError by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<AccountEntity?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
    deleting?.let { account ->
        ConfirmDialog(
            title = "Delete ${account.displayName()}?",
            text = "For an account added by mistake. Its opening balance entry goes with it, and this can't be undone.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteAccount(account.id) { deleteError = it } },
            onDismiss = { deleting = null },
        )
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = status.calculateTopPadding())) {
            DetailTopBar(title = "Accounts & categories", onBack = onBack)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = margin, top = MoneyTheme.spacing.sm, end = margin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(MoneyTheme.spacing.gutter),
        ) {
            item("add-toggle") {
                Pill(if (showAdd) "Cancel" else "+ Add account or category", onClick = { showAdd = !showAdd })
            }
            if (showAdd) {
                item("add-form") {
                    Module {
                        FormField("Name", newName, onValueChange = { newName = it; addError = null }, placeholder = "e.g. Groceries", error = addError)
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccountType.entries.forEach { type ->
                                Pill(type.name, onClick = { newType = type }, style = if (newType == type) PillStyle.Primary else PillStyle.Secondary)
                            }
                        }
                        Pill(
                            "Add",
                            onClick = {
                                val trimmed = newName.trim()
                                if (trimmed.isEmpty()) {
                                    addError = "Give it a name"
                                } else {
                                    val name = if (newType == AccountType.Expense) "Expenses:$trimmed" else trimmed
                                    viewModel.addAccount(name, newType)
                                    newName = ""
                                    showAdd = false
                                }
                            },
                            style = PillStyle.Primary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            deleteError?.let { message ->
                item("delete-error") { Text(message, style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.negative) }
            }
            snapshot?.let { snap ->
                item("sits") { MoneySitsModule(moneyMap(snap)) }
            }
            item("list") {
                Module {
                    ModuleHeader("Every account and category")
                    accounts.forEachIndexed { i, account ->
                        AccountRow(
                            account,
                            isEssential = account.displayName() in essentials,
                            viewModel = viewModel,
                            divider = i < accounts.lastIndex,
                            isEditing = editingId == account.id,
                            onEdit = { editingId = if (it) account.id else null },
                            onDelete = { deleteError = null; deleting = account },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountEntity,
    isEssential: Boolean,
    viewModel: SettingsViewModel,
    divider: Boolean,
    isEditing: Boolean,
    onEdit: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    if (isEditing) {
        if (account.type == AccountType.Asset) AssetEditor(account, viewModel, divider, onClose = { onEdit(false) })
        else CategoryEditor(account, viewModel, divider, onClose = { onEdit(false) })
        return
    }
    Column {
        ListRow(
            title = account.displayName(),
            subtitle = account.type.name +
                (if (isEssential) " · essential" else "") +
                (if (account.isArchived) " · archived" else "") +
                (if (!account.inNetWorth) " · not in net worth" else "") +
                (effectiveTier(account)?.let { " · ${it.label()}" } ?: "") +
                (account.goal?.let { " · ${it.label()}" } ?: ""),
            divider = false,
            trailing = {
                if (account.type == AccountType.Expense) {
                    Toggle(isEssential, onCheckedChange = { viewModel.toggleEssential(account.displayName()) })
                }
                if (account.type == AccountType.Asset) {
                    // e.g. EPF: tracked, but kept out of net worth, the wealth goal and runway.
                    Pill(
                        if (account.inNetWorth) "In net worth" else "Excluded",
                        onClick = { viewModel.setInNetWorth(account.id, !account.inNetWorth) },
                        style = if (account.inNetWorth) PillStyle.Secondary else PillStyle.Primary,
                        height = 28.dp,
                    )
                }
            },
        )
        Row(
            Modifier.padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (account.type == AccountType.Expense) {
                Pill("Edit", onClick = { onEdit(true) }, height = 28.dp)
            }
            if (account.type == AccountType.Asset) {
                Pill("Liquidity & goal", onClick = { onEdit(true) }, height = 28.dp)
            }
            Pill(
                if (account.isArchived) "Unarchive" else "Archive",
                onClick = { if (account.isArchived) viewModel.unarchiveAccount(account.id) else viewModel.archiveAccount(account.id) },
                height = 28.dp,
            )
            if (account.type != AccountType.Equity) Pill("Delete", onClick = onDelete, style = PillStyle.Destructive, height = 28.dp)
        }
        if (divider) RowDivider()
    }
}

/** Renames a category in place; the essential flag and past entries stay with it. */
@Composable
private fun CategoryEditor(account: AccountEntity, viewModel: SettingsViewModel, divider: Boolean, onClose: () -> Unit) {
    var name by rememberSaveable(account.id) { mutableStateOf(account.displayName()) }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        FormField("Category name", name, onValueChange = { name = it; error = null }, modifier = Modifier.padding(top = 10.dp), error = error)
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                "Save",
                onClick = { viewModel.renameCategory(account, name) { problem -> if (problem == null) onClose() else error = problem } },
                style = PillStyle.Primary,
                height = 28.dp,
            )
            Pill("Cancel", onClick = onClose, height = 28.dp)
        }
        if (divider) RowDivider()
    }
}

/** Chooses how liquid an asset is and which goal its balance counts towards. */
@Composable
private fun AssetEditor(account: AccountEntity, viewModel: SettingsViewModel, divider: Boolean, onClose: () -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(account.displayName(), style = MoneyTheme.type.bodyLg, color = MoneyTheme.colors.ink)
        Text("Liquidity", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Automatic", { viewModel.setLiquidityTier(account.id, null) }, style = if (account.liquidityTier == null) PillStyle.Primary else PillStyle.Secondary, height = 28.dp)
            LiquidityTier.entries.forEach { t ->
                Pill(t.label(), { viewModel.setLiquidityTier(account.id, t) }, style = if (account.liquidityTier == t) PillStyle.Primary else PillStyle.Secondary, height = 28.dp)
            }
        }
        Text(
            "Automatic is ${effectiveTier(account)?.label()} for this kind of account. Cash is spendable now, Deposits can be broken, Markets can be sold, Locked can't be reached soon (like EPF).",
            style = MoneyTheme.type.bodySm, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 6.dp),
        )
        Text("Counts towards", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("No goal", { viewModel.setGoal(account.id, null) }, style = if (account.goal == null) PillStyle.Primary else PillStyle.Secondary, height = 28.dp)
            AccountGoal.entries.forEach { g ->
                Pill(g.label(), { viewModel.setGoal(account.id, g) }, style = if (account.goal == g) PillStyle.Primary else PillStyle.Secondary, height = 28.dp)
            }
        }
        Pill("Done", onClose, style = PillStyle.Primary, height = 28.dp, modifier = Modifier.padding(vertical = 10.dp))
        if (divider) RowDivider()
    }
}

/** Your assets by how liquid they are and by goal, at book value. */
@Composable
internal fun MoneySitsModule(map: com.moneymatters.app.core.ledger.MoneyMap) {
    Module {
        ModuleHeader("Where your money sits", subtitle = "At book value")
        LiquidityTier.entries.forEachIndexed { i, t -> KeyValueRow(t.label(), (map.byTier[t] ?: com.moneymatters.app.core.money.Paise.Zero).formatInr(), divider = i < LiquidityTier.entries.lastIndex) }
        Text("Towards goals", style = MoneyTheme.type.labelUppercase, color = MoneyTheme.colors.muted, modifier = Modifier.padding(top = 14.dp))
        AccountGoal.entries.forEach { g -> KeyValueRow(g.label(), (map.byGoal[g] ?: com.moneymatters.app.core.money.Paise.Zero).formatInr()) }
        KeyValueRow("Not counted towards a goal", map.unassigned.formatInr(), divider = false)
    }
}
