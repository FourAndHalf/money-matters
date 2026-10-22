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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.feature.books.displayName
import com.moneymatters.app.ui.components.DetailTopBar
import com.moneymatters.app.ui.components.FormField
import com.moneymatters.app.ui.components.ListRow
import com.moneymatters.app.ui.components.Module
import com.moneymatters.app.ui.components.ModuleHeader
import com.moneymatters.app.ui.components.Pill
import com.moneymatters.app.ui.components.PillStyle
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

    val margin = MoneyTheme.spacing.margin
    val status = WindowInsets.statusBars.asPaddingValues()
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
                        FormField("Name", newName, onValueChange = { newName = it }, placeholder = "e.g. Groceries")
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccountType.entries.forEach { type ->
                                Pill(type.name, onClick = { newType = type }, style = if (newType == type) PillStyle.Primary else PillStyle.Secondary)
                            }
                        }
                        Pill(
                            "Add",
                            onClick = {
                                val name = if (newType == AccountType.Expense) "Expenses:$newName" else newName
                                viewModel.addAccount(name, newType)
                                newName = ""
                                showAdd = false
                            },
                            style = PillStyle.Primary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            item("list") {
                Module {
                    ModuleHeader("Every account and category")
                    accounts.forEachIndexed { i, account ->
                        AccountRow(account, isEssential = account.displayName() in essentials, viewModel = viewModel, divider = i < accounts.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: AccountEntity, isEssential: Boolean, viewModel: SettingsViewModel, divider: Boolean) {
    ListRow(
        title = account.displayName(),
        subtitle = account.type.name + (if (account.isArchived) " · archived" else "") + (if (!account.inNetWorth) " · not in net worth" else ""),
        divider = divider,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Pill(
                    if (account.isArchived) "Unarchive" else "Archive",
                    onClick = { if (account.isArchived) viewModel.unarchiveAccount(account.id) else viewModel.archiveAccount(account.id) },
                    style = if (account.isArchived) PillStyle.Secondary else PillStyle.Destructive,
                    height = 28.dp,
                )
            }
        },
    )
}
