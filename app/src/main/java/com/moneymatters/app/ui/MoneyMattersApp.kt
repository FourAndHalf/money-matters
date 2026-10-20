package com.moneymatters.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moneymatters.app.feature.accounts.AccountLedgerScreen
import com.moneymatters.app.feature.accounts.AccountsScreen
import com.moneymatters.app.feature.dashboard.HomeScreen
import com.moneymatters.app.feature.books.BooksState
import com.moneymatters.app.feature.quickadd.QuickAddPrefs
import com.moneymatters.app.feature.quickadd.QuickAddScreen
import com.moneymatters.app.feature.quickadd.QuickAddViewModel
import com.moneymatters.app.feature.settings.SettingsScreen
import com.moneymatters.app.feature.settings.SettingsViewModel
import com.moneymatters.app.feature.transactions.TransactionDetailScreen
import com.moneymatters.app.feature.transactions.TransactionsScreen
import com.moneymatters.app.ui.components.MoneyBottomBar
import com.moneymatters.app.ui.components.NavItem
import com.moneymatters.app.ui.components.QuickAddFab
import com.moneymatters.app.ui.icons.MoneyIcons
import com.moneymatters.app.ui.theme.MoneyTheme

private val Tabs = listOf(
    NavItem("Home", MoneyIcons.Home),
    NavItem("Txns", MoneyIcons.Transactions),
    NavItem("Accounts", MoneyIcons.Accounts),
    NavItem("Invest", MoneyIcons.Invest),
    NavItem("More", MoneyIcons.More),
)

private const val TXNS_TAB = 1
private const val ACCOUNTS_TAB = 2
private const val MORE_TAB = 4

/** App shell: canvas, current tab, Quick Add and the floating bottom bar. */
@Composable
fun MoneyMattersApp(books: BooksState, quickAdd: QuickAddViewModel, quickAddPrefs: QuickAddPrefs, settings: SettingsViewModel) {
    val accounts = books.accounts
    val ledgers = books.ledgers
    val transactions = books.transactions
    val transactionDetails = books.details
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var openTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val c = MoneyTheme.colors
    val canvas = if (c.isDark) Brush.verticalGradient(listOf(c.canvas, c.canvas)) else Brush.verticalGradient(0f to Color(0xFFF0F0F2), 0.45f to c.canvas)

    Box(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .background(canvas),
    ) {
        val openLedger = openAccountId?.let { ledgers[it] }
        val openTransaction = openTransactionId?.let { transactionDetails[it] }
        when {
            // Drilling into an account, a transaction or Quick Add hides the tab bar.
            openLedger != null -> {
                BackHandler { openAccountId = null }
                AccountLedgerScreen(openLedger, onBack = { openAccountId = null })
            }
            openTransaction != null -> {
                BackHandler { openTransactionId = null }
                TransactionDetailScreen(openTransaction, onBack = { openTransactionId = null })
            }
            showQuickAdd -> {
                BackHandler { showQuickAdd = false }
                QuickAddScreen(quickAdd, quickAddPrefs, onClose = { quickAdd.reset(); showQuickAdd = false })
            }
            showSettings -> {
                BackHandler { showSettings = false }
                SettingsScreen(settings, onClose = { showSettings = false })
            }
            else -> {
                when (tab) {
                    0 -> HomeScreen(
                        books.home,
                        onOpenAccounts = { tab = ACCOUNTS_TAB },
                        onOpenAccount = { id -> openAccountId = id },
                        onOpenTransactions = { tab = TXNS_TAB },
                        onOpenTransaction = { id -> openTransactionId = id },
                        // Clears the floating bar and Quick Add.
                        contentPadding = PaddingValues(bottom = 132.dp),
                    )
                    TXNS_TAB -> TransactionsScreen(
                        transactions,
                        onOpenTransaction = { id -> openTransactionId = id },
                        contentPadding = PaddingValues(bottom = 132.dp),
                    )
                    ACCOUNTS_TAB -> AccountsScreen(
                        accounts,
                        onOpenAccount = { id -> openAccountId = id },
                        contentPadding = PaddingValues(bottom = 132.dp),
                    )
                    else -> Text(
                        "${Tabs[tab].label} is not built yet",
                        style = MoneyTheme.type.bodyMd,
                        color = c.muted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                ) {
                    MoneyBottomBar(Tabs, tab, onSelect = { if (it == MORE_TAB) showSettings = true else tab = it })
                }
                QuickAddFab(
                    onClick = { showQuickAdd = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 22.dp, bottom = 98.dp),
                )
            }
        }
    }
}
