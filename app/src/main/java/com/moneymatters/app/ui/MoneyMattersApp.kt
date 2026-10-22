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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.RestateKind
import com.moneymatters.app.core.invest.TradeSide
import com.moneymatters.app.feature.books.BooksState
import com.moneymatters.app.feature.invest.ActionScreen
import com.moneymatters.app.feature.invest.AllotScreen
import com.moneymatters.app.feature.invest.DepositCloseScreen
import com.moneymatters.app.feature.invest.DepositDetailScreen
import com.moneymatters.app.feature.invest.DepositScreen
import com.moneymatters.app.feature.invest.DividendScreen
import com.moneymatters.app.feature.invest.HoldingDetailScreen
import com.moneymatters.app.feature.invest.ImportScreen
import com.moneymatters.app.feature.invest.InvestScreen
import com.moneymatters.app.feature.invest.InvestViewModel
import com.moneymatters.app.feature.invest.PriceScreen
import com.moneymatters.app.feature.invest.PricesScreen
import com.moneymatters.app.feature.invest.SipDetailScreen
import com.moneymatters.app.feature.invest.SipFormScreen
import com.moneymatters.app.feature.invest.TradeScreen
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
private const val INVEST_TAB = 3
private const val MORE_TAB = 4

private enum class InvestOverlay { Trade, Price, Import, Dividend, Action, Allot, NewDeposit, CloseDeposit, NewSip }

/** App shell: canvas, current tab, Quick Add and the floating bottom bar. */
@Composable
fun MoneyMattersApp(books: BooksState, quickAdd: QuickAddViewModel, quickAddPrefs: QuickAddPrefs, settings: SettingsViewModel, invest: InvestViewModel) {
    val accounts = books.accounts
    val ledgers = books.ledgers
    val transactions = books.transactions
    val transactionDetails = books.details
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var openTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var openHoldingId by rememberSaveable { mutableStateOf<String?>(null) }
    var openDepositId by rememberSaveable { mutableStateOf<String?>(null) }
    var openSipId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPrices by rememberSaveable { mutableStateOf(false) }
    var investOverlay by rememberSaveable { mutableStateOf<InvestOverlay?>(null) }
    val investState = invest.state.collectAsState().value
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
        val openHolding = openHoldingId?.let { investState?.details?.get(it) }
        val openDeposit = openDepositId?.let { investState?.depositDetails?.get(it) }
        val openSip = openSipId?.let { investState?.sipDetails?.get(it) }
        when {
            investOverlay == InvestOverlay.Trade -> {
                BackHandler { investOverlay = null }
                TradeScreen(invest, onClose = { investOverlay = null })
            }
            investOverlay == InvestOverlay.Price -> {
                BackHandler { investOverlay = null; invest.priceForm = null }
                val priceHolding = invest.priceForm?.holdingId
                PriceScreen(invest, holdingName = investState?.details?.get(priceHolding)?.row?.name.orEmpty(), onClose = { investOverlay = null })
            }
            investOverlay == InvestOverlay.Import -> {
                BackHandler { investOverlay = null; invest.resetImport() }
                ImportScreen(invest, onClose = { investOverlay = null; invest.resetImport() })
            }
            investOverlay == InvestOverlay.Dividend -> {
                BackHandler { investOverlay = null; invest.dividendForm = null }
                DividendScreen(invest, holdingName = openHolding?.row?.name.orEmpty(), symbol = openHolding?.row?.symbol.orEmpty(), onClose = { investOverlay = null })
            }
            investOverlay == InvestOverlay.Action -> {
                BackHandler { investOverlay = null; invest.actionForm = null }
                ActionScreen(invest, holdingName = openHolding?.row?.name.orEmpty(), onClose = { investOverlay = null })
            }
            showPrices && investState != null && openHolding == null -> {
                BackHandler { showPrices = false }
                PricesScreen(
                    investState.prices, invest.sources, invest.isRefreshing,
                    onBack = { showPrices = false },
                    onRefresh = invest::refreshPrices,
                    onUpdateManual = { id -> invest.startPrice(id); investOverlay = InvestOverlay.Price },
                    onConfirm = invest::confirmPrice,
                    onResolve = invest::resolveMismatch,
                    onSaveSources = invest::saveSources,
                )
            }
            investOverlay == InvestOverlay.NewSip -> {
                BackHandler { investOverlay = null; invest.sipForm = null }
                SipFormScreen(invest, funds = investState?.holdings?.filter { it.instrument == Instrument.MutualFund }.orEmpty(), onClose = { investOverlay = null })
            }
            openSip != null -> {
                BackHandler { openSipId = null }
                SipDetailScreen(
                    openSip,
                    onBack = { openSipId = null },
                    onPay = { invest.payInstalment(openSip.row.planId, it.due) },
                    onSkip = { invest.skipInstalment(openSip.row.planId, it.due) },
                    onStop = { invest.stopSip(openSip.row.planId); openSipId = null },
                )
            }
            investOverlay == InvestOverlay.NewDeposit -> {
                BackHandler { investOverlay = null; invest.depositForm = null }
                DepositScreen(invest, onClose = { investOverlay = null })
            }
            investOverlay == InvestOverlay.CloseDeposit -> {
                BackHandler { investOverlay = null; invest.closeForm = null }
                if (openDeposit != null) DepositCloseScreen(invest, openDeposit, onClose = { investOverlay = null; openDepositId = null }) else LaunchedEffect(Unit) { investOverlay = null }
            }
            openDeposit != null -> {
                BackHandler { openDepositId = null }
                DepositDetailScreen(
                    openDeposit,
                    onBack = { openDepositId = null },
                    onPostInterest = { invest.postInterest(openDeposit.row.accountId) },
                    onClose = { invest.startClose(openDeposit.row.accountId); investOverlay = InvestOverlay.CloseDeposit },
                )
            }
            investOverlay == InvestOverlay.Allot -> {
                BackHandler { investOverlay = null; invest.allotForm = null }
                val order = investState?.pending?.firstOrNull { it.id == invest.allotForm?.pendingId }
                if (order != null) AllotScreen(invest, order, onClose = { investOverlay = null }) else LaunchedEffect(Unit) { investOverlay = null }
            }
            openHolding != null -> {
                BackHandler { openHoldingId = null }
                HoldingDetailScreen(
                    openHolding,
                    onBack = { openHoldingId = null },
                    onBuy = { invest.startTrade(TradeSide.Buy, openHolding.row.id, openHolding.row.instrument); investOverlay = InvestOverlay.Trade },
                    onSell = { invest.startTrade(TradeSide.Sell, openHolding.row.id, openHolding.row.instrument); investOverlay = InvestOverlay.Trade },
                    onUpdatePrice = { invest.startPrice(openHolding.row.id); investOverlay = InvestOverlay.Price },
                    onDividend = { invest.startDividend(openHolding.row.id); investOverlay = InvestOverlay.Dividend },
                    onCorporateAction = { invest.startAction(openHolding.row.id, RestateKind.Split); investOverlay = InvestOverlay.Action },
                )
            }
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
                    INVEST_TAB -> investState?.let {
                        InvestScreen(
                            it,
                            onOpenHolding = { id -> openHoldingId = id },
                            onBuy = { invest.startTrade(TradeSide.Buy); investOverlay = InvestOverlay.Trade },
                            onImport = { invest.resetImport(); investOverlay = InvestOverlay.Import },
                            onAllot = { id -> invest.startAllot(id); investOverlay = InvestOverlay.Allot },
                            onNewDeposit = { invest.startDeposit(); investOverlay = InvestOverlay.NewDeposit },
                            onNewSip = { invest.startSip(); investOverlay = InvestOverlay.NewSip },
                            onOpenPrices = { showPrices = true },
                            onOpenSip = { id -> openSipId = id },
                            onOpenDeposit = { id -> openDepositId = id },
                            onCancelOrder = invest::cancelOrder,
                            contentPadding = PaddingValues(bottom = 132.dp),
                        )
                    }
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
