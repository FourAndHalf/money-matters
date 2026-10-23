package com.moneymatters.app.feature.invest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.invest.CapitalGainsRules
import com.moneymatters.app.core.invest.CorporateActionKind
import com.moneymatters.app.core.invest.FundClass
import com.moneymatters.app.core.ledger.ClosedPeriodException
import com.moneymatters.app.core.ledger.IllegalLifecycleTransitionException
import com.moneymatters.app.feature.transactions.plainRupees
import com.moneymatters.app.core.invest.InsufficientUnitsException
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.invest.LockedUnitsException
import com.moneymatters.app.core.invest.InvestRepository
import com.moneymatters.app.core.invest.AllocationPrefs
import com.moneymatters.app.core.invest.PriceSourcePrefs
import com.moneymatters.app.core.invest.PriceSources
import com.moneymatters.app.core.invest.PriceStatus
import com.moneymatters.app.core.invest.PriceUpdater
import com.moneymatters.app.core.invest.RestateKind
import com.moneymatters.app.core.invest.TradeSide
import com.moneymatters.app.core.invest.formatUnits
import com.moneymatters.app.core.ledger.AccountEntity
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.LedgerSnapshot
import com.moneymatters.app.core.money.impliedPrice
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val OPENING_EQUITY = "Equity:Opening"
private const val DEFAULT_EQUITY_TARGET = 60

/** The accounts a stock can be paid for from or sold into: live assets that aren't holdings or other investments. */
internal fun settlementAccountsIn(ledger: LedgerSnapshot): List<AccountEntity> =
    ledger.accounts
        .filter { it.type == AccountType.Asset && !it.isArchived && it.id !in ledger.holdingAccountIds && it.kind != AccountKind.Investment }
        .sortedBy { it.name.lowercase() }

/** Streams the Invest tab's state and holds the buy/sell, price and import forms (plan.html → P13–P15). */
class InvestViewModel(
    private val ledger: LedgerRepository,
    private val invest: InvestRepository,
    private val rules: CapitalGainsRules,
    private val today: () -> LocalDate = LocalDate::now,
    /** The wealth goal SIP shortfalls are measured against, if the investor has one. */
    private val goal: () -> SipGoal? = { null },
    private val priceUpdater: PriceUpdater? = null,
    private val sourcePrefs: PriceSourcePrefs? = null,
    private val allocationPrefs: AllocationPrefs? = null,
) : ViewModel() {

    private val targetEquity = MutableStateFlow(allocationPrefs?.targetEquityPercent())

    /** Moves the equity target by [delta] points (starting from 60 when none is set), keeping it between 0 and 100. */
    fun nudgeTarget(delta: Int) {
        val next = ((targetEquity.value ?: DEFAULT_EQUITY_TARGET) + delta).coerceIn(0, 100)
        allocationPrefs?.save(next)
        targetEquity.value = next
    }

    val state: StateFlow<InvestState?> = combine(ledger.snapshot(), invest.snapshot(), targetEquity) { l, i, target -> buildInvest(l, i, today(), rules, goal(), targetEquityPercent = target) }
        .flowOn(Dispatchers.Default)
        // A failure building the screens' state is reported, never allowed to take the app down.
        .catch { error = "Couldn't read your investments: ${it.message ?: it.javaClass.simpleName}" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Real accounts money can be paid from or received into: assets that aren't themselves holdings. */
    val settlementAccounts: StateFlow<List<AccountEntity>> = ledger.snapshot()
        .map(::settlementAccountsIn)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Buy / sell ----
    var tradeForm by mutableStateOf(TradeForm.empty(TradeSide.Buy, today()))
    var isSaving by mutableStateOf(false)
        private set

    /** A plain-language reason the last save didn't go through, or null. */
    var error by mutableStateOf<String?>(null)
        private set

    fun startTrade(side: TradeSide, holdingId: String? = null, instrument: Instrument = Instrument.Stock) {
        tradeForm = TradeForm.empty(side, today(), holdingId, instrument)
        error = null
    }

    fun saveTrade(onSaved: () -> Unit) {
        val form = tradeForm
        val amount = form.amount
        val accountId = form.accountId
        if (!form.canSave || amount == null || accountId == null || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                val editing = form.editingTransactionId
                if (editing != null) {
                    invest.editTrade(editing, form.date, form.units ?: return@launch, amount, accountId)
                    tradeForm = TradeForm.empty(TradeSide.Buy, today())
                    onSaved()
                    return@launch
                }
                val symbol = form.symbol.trim().uppercase().ifBlank { null }
                val holdingId = form.holdingId ?: invest.findOrCreateHolding(
                    form.instrument, symbol, form.isin.trim().uppercase().ifBlank { null }, form.name.trim().ifBlank { null },
                    fundClass = if (form.isFund) form.fundClass else null,
                    exitLoadDays = form.exitLoadDaysText.trim().toIntOrNull(),
                    couponBps = if (form.isBond) form.couponBps else null,
                    maturityDate = if (form.isBond) form.maturity else null,
                )
                val label = symbol ?: form.name.trim().ifBlank { "holding" }
                val units = form.units
                when {
                    form.inTransit -> invest.orderFund(holdingId, form.date, amount, accountId, "Buy $label (in transit)")
                    units == null -> return@launch
                    form.side == TradeSide.Buy -> invest.buy(holdingId, form.date, units, impliedPrice(units, amount), amount, accountId, "Buy $label")
                    else -> invest.sell(holdingId, form.date, units, impliedPrice(units, amount), amount, accountId, "Sell $label")
                }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: InsufficientUnitsException) {
                error = "That's more than you held on that date."
            } catch (e: LockedUnitsException) {
                error = (e.message ?: "Some of those units are locked") + "."
            } catch (e: IllegalLifecycleTransitionException) {
                error = e.message
            } catch (e: ClosedPeriodException) {
                error = e.message
            } catch (_: Exception) {
                error = "Couldn't save the trade. Nothing was changed."
            } finally {
                isSaving = false
            }
        }
    }

    // ---- Prices (P15) ----
    var isRefreshing by mutableStateOf(false)
        private set

    /** The source addresses as currently saved; editing them goes through [saveSources]. */
    var sources by mutableStateOf(sourcePrefs?.sources() ?: PriceSources.Default)
        private set

    /** Downloads prices now (the weekly job does the same on a schedule). Each source reports itself in the log. */
    fun refreshPrices() {
        val updater = priceUpdater ?: return
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            try {
                updater.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "The price download stopped unexpectedly."
            } finally {
                isRefreshing = false
            }
        }
    }

    fun saveSources(newSources: PriceSources) {
        sourcePrefs?.save(newSources)
        sources = newSources
    }

    fun confirmPrice(priceId: Long) = launchPrices { invest.confirmPrice(priceId) }

    fun resolveMismatch(holdingId: String, dateIso: String, keepDownloaded: Boolean) =
        launchPrices { invest.resolveMismatch(holdingId, LocalDate.parse(dateIso), keepDownloaded) }

    private fun launchPrices(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't update that price."
            }
        }
    }

    // ---- SIPs ----
    var sipForm by mutableStateOf<SipForm?>(null)

    fun startSip() {
        sipForm = SipForm(start = today())
        error = null
    }

    fun saveSip(onSaved: () -> Unit) {
        val form = sipForm ?: return
        val holdingId = form.holdingId ?: return
        val amount = form.amount ?: return
        val day = form.day ?: return
        val accountId = form.accountId ?: return
        viewModelScope.launch {
            try {
                form.editingPlanId?.let { invest.updateSip(it, amount, day, accountId) } ?: invest.createSipPlan(holdingId, amount, day, accountId, form.start)
                sipForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't save the SIP."
            }
        }
    }

    /** Confirms a due instalment as paid; it goes In Transit and can then be allotted. */
    fun payInstalment(planId: String, due: LocalDate) = launchSip { invest.payInstalment(planId, due) }

    fun skipInstalment(planId: String, due: LocalDate) = launchSip { invest.skipInstalment(planId, due) }

    fun stopSip(planId: String) = launchSip { invest.stopSip(planId, today()) }

    private fun launchSip(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't update the SIP."
            }
        }
    }

    // ---- Fixed deposits ----
    var depositForm by mutableStateOf<DepositForm?>(null)

    fun startDeposit() {
        depositForm = DepositForm(start = today(), maturity = today().plusYears(1))
        error = null
    }

    fun saveDeposit(onSaved: () -> Unit) {
        val form = depositForm ?: return
        val principal = form.principal ?: return
        val rateBps = form.rateBps ?: return
        val penaltyBps = form.penaltyBps ?: return
        if (!form.canSave || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                val editing = form.editingAccountId
                if (editing != null) {
                    invest.updateDeposit(editing, form.name, principal, rateBps, form.compounding, form.start, form.maturity, form.autoRenew, penaltyBps)
                } else {
                    val from = if (form.existing) ledger.createAccountIfAbsent(OPENING_EQUITY, AccountType.Equity) else form.accountId!!
                    invest.openDeposit(form.name, principal, rateBps, form.compounding, form.start, form.maturity, from, form.autoRenew, penaltyBps)
                }
                depositForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                error = e.message
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (_: Exception) {
                error = "Couldn't save the deposit. Nothing was changed."
            } finally {
                isSaving = false
            }
        }
    }

    /** Books the interest earned to today as income. */
    fun postInterest(depositAccountId: String) {
        viewModelScope.launch {
            try {
                invest.accrueInterest(depositAccountId, today())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't post the interest."
            }
        }
    }

    var closeForm by mutableStateOf<DepositCloseForm?>(null)

    fun startClose(depositAccountId: String) {
        closeForm = DepositCloseForm(depositAccountId, today())
        error = null
    }

    fun saveClose(onSaved: () -> Unit) {
        val form = closeForm ?: return
        val tds = form.tds ?: return
        if (!form.canSave || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                invest.closeDeposit(form.accountId, form.date, form.toAccountId, tds, form.renew)
                closeForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (_: Exception) {
                error = "Couldn't close the deposit. Nothing was changed."
            } finally {
                isSaving = false
            }
        }
    }

    // ---- Allotment of a fund order waiting In Transit ----
    var allotForm by mutableStateOf<AllotForm?>(null)

    fun startAllot(pendingId: String) {
        allotForm = AllotForm(pendingId, today())
        error = null
    }

    fun saveAllot(onSaved: () -> Unit) {
        val form = allotForm ?: return
        val units = form.units ?: return
        val nav = form.nav ?: return
        viewModelScope.launch {
            try {
                invest.allot(form.pendingId, form.date, units, nav)
                allotForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't allot the units. Check the date is on or after the order."
            }
        }
    }

    fun cancelOrder(pendingId: String) {
        viewModelScope.launch {
            try {
                invest.cancelPending(pendingId, today())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't cancel that order."
            }
        }
    }

    // ---- Dividend ----
    var dividendForm by mutableStateOf<DividendForm?>(null)

    fun startDividend(holdingId: String) {
        dividendForm = DividendForm(holdingId, today())
        error = null
    }

    fun saveDividend(symbol: String, onSaved: () -> Unit) {
        val form = dividendForm ?: return
        val net = form.net ?: return
        val tds = form.tds ?: return
        val accountId = form.accountId ?: return
        viewModelScope.launch {
            try {
                val coupon = invest.holdings().firstOrNull { it.id == form.holdingId }?.instrument == Instrument.Bond
                invest.recordDividend(form.holdingId, form.date, net, tds, accountId, if (coupon) "Coupon $symbol" else "Dividend $symbol", if (coupon) CorporateActionKind.Coupon else CorporateActionKind.Dividend)
                dividendForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't save the dividend."
            }
        }
    }

    // ---- Split / bonus ----
    var actionForm by mutableStateOf<ActionForm?>(null)

    fun startAction(holdingId: String, kind: RestateKind) {
        actionForm = ActionForm(holdingId, kind, today())
        error = null
    }

    fun saveAction(onSaved: () -> Unit) {
        val form = actionForm ?: return
        val ratio = form.ratio ?: return
        viewModelScope.launch {
            try {
                invest.applyCorporateAction(form.holdingId, form.kind, ratio, form.date)
                actionForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = "Nothing was held on that date."
            } catch (_: Exception) {
                error = "Couldn't save the corporate action. Nothing was changed."
            }
        }
    }

    // ---- Manual price ----
    var priceForm by mutableStateOf<PriceForm?>(null)

    fun startPrice(holdingId: String) {
        priceForm = PriceForm(holdingId, today())
        error = null
    }

    fun savePrice(onSaved: () -> Unit) {
        val form = priceForm ?: return
        val price = form.price ?: return
        viewModelScope.launch {
            try {
                invest.recordPrice(form.holdingId, form.date, price, "manual", PriceStatus.Manual)
                priceForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't save the price."
            }
        }
    }

    // ---- Editing and deleting what was entered ----

    /** Runs a write and reports a refusal in plain words (a reconciled entry, a closed month, a later sale) instead of crashing. */
    private fun launchWrite(failure: String, onDone: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            manageError = null
            try {
                block()
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                manageError = e.message ?: failure
            } catch (e: IllegalArgumentException) {
                manageError = e.message ?: failure
            } catch (_: Exception) {
                manageError = failure
            }
        }
    }

    /** Why the last delete from a detail page was refused; shown on that page only. */
    var manageError by mutableStateOf<String?>(null)
        private set

    fun clearManageError() { manageError = null }

    fun deleteHolding(holdingId: String, onDone: () -> Unit) = launchWrite("Couldn't delete it. Nothing was changed.", onDone) { invest.deleteHolding(holdingId) }

    fun deleteTrade(transactionId: String) = launchWrite("Couldn't delete the trade. Nothing was changed.") { invest.deleteTrade(transactionId) }

    fun deleteIncome(transactionId: String) = launchWrite("Couldn't delete it. Nothing was changed.") { invest.deleteIncome(transactionId) }

    fun deleteDeposit(accountId: String, onDone: () -> Unit) = launchWrite("Couldn't delete the deposit. Nothing was changed.", onDone) { invest.deleteDeposit(accountId) }

    fun deleteSip(planId: String, onDone: () -> Unit) = launchWrite("Couldn't delete the SIP. Nothing was changed.", onDone) { invest.deleteSip(planId) }

    /** Opens a buy or sell for correction, with its date, units, amount and account filled in; [onReady] fires once the form is loaded. */
    fun startEditTrade(transactionId: String, onReady: () -> Unit) {
        error = null
        viewModelScope.launch {
            try {
                val l = ledger.snapshot().first()
                val i = invest.snapshot().first()
                val txn = l.transactions.first { it.id == transactionId }
                val legs = l.postings.filter { it.transactionId == transactionId }
                val holdingAccounts = i.holdings.associateBy { it.accountId }
                val holdingLeg = legs.first { it.accountId in holdingAccounts }
                val holding = holdingAccounts.getValue(holdingLeg.accountId)
                val isBuy = (holdingLeg.debitPaise ?: 0) > 0
                val income = l.accounts.filter { it.type == AccountType.Income }.map { it.id }.toSet()
                val money = legs.first { it.accountId !in holdingAccounts && it.accountId !in income }
                tradeForm = TradeForm(
                    side = if (isBuy) TradeSide.Buy else TradeSide.Sell,
                    date = LocalDate.parse(txn.date),
                    holdingId = holding.id,
                    instrument = holding.instrument,
                    unitsText = formatUnits(com.moneymatters.app.core.money.Units(kotlin.math.abs(holdingLeg.units ?: 0))),
                    amountText = com.moneymatters.app.core.money.Paise(money.debitPaise ?: money.creditPaise ?: 0).plainRupees(),
                    accountId = money.accountId,
                    editingTransactionId = transactionId,
                )
                onReady()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't open that trade."
            }
        }
    }

    var holdingEditForm by mutableStateOf<HoldingEditForm?>(null)

    fun startEditHolding(holdingId: String, onReady: () -> Unit) {
        error = null
        viewModelScope.launch {
            try {
                val holding = invest.holdings().first { it.id == holdingId }
                val accountName = ledger.snapshot().first().accounts.first { it.id == holding.accountId }.name
                holdingEditForm = HoldingEditForm(
                    holdingId = holding.id,
                    instrument = holding.instrument,
                    name = if (holding.instrument == Instrument.Stock) accountName.substringBefore(" (").takeIf { holding.symbol != null && accountName != holding.symbol } ?: "" else accountName,
                    symbol = holding.symbol.orEmpty(),
                    isin = holding.isin.orEmpty(),
                    fundClass = holding.fundClass ?: FundClass.Equity,
                    exitLoadDaysText = holding.exitLoadDays?.toString().orEmpty(),
                    couponText = holding.couponBps?.let { formatBps(it).removeSuffix("%") }.orEmpty(),
                    maturity = holding.maturityDate?.let(LocalDate::parse),
                )
                onReady()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't open that holding."
            }
        }
    }

    fun saveHoldingEdit(onSaved: () -> Unit) {
        val form = holdingEditForm ?: return
        if (!form.canSave || isSaving) return
        viewModelScope.launch {
            isSaving = true
            error = null
            try {
                invest.updateHolding(form.holdingId, form.name, form.symbol, form.isin, form.fundClass, form.exitLoadDays, form.couponBps, form.maturity)
                holdingEditForm = null
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (_: Exception) {
                error = "Couldn't save the changes. Nothing was changed."
            } finally {
                isSaving = false
            }
        }
    }

    /** Opens a deposit for correction with its current terms filled in. */
    fun startEditDeposit(accountId: String, onReady: () -> Unit) {
        error = null
        viewModelScope.launch {
            try {
                val d = invest.snapshot().first().fixedDeposits.first { it.accountId == accountId }
                val name = ledger.snapshot().first().accounts.first { it.id == accountId }.name
                depositForm = DepositForm(
                    name = name, start = LocalDate.parse(d.startDate), maturity = LocalDate.parse(d.maturityDate),
                    principalText = com.moneymatters.app.core.money.Paise(d.principalPaise).plainRupees(),
                    rateText = formatBps(d.rateBps).removeSuffix("%"), compounding = d.compounding,
                    penaltyText = formatBps(d.penaltyBps).removeSuffix("%"), autoRenew = d.autoRenew,
                    editingAccountId = accountId,
                )
                onReady()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't open that deposit."
            }
        }
    }

    /** Opens a SIP for correction with its current amount, day and account filled in. */
    fun startEditSip(planId: String, onReady: () -> Unit) {
        error = null
        viewModelScope.launch {
            try {
                val p = invest.snapshot().first().sipPlans.first { it.id == planId }
                sipForm = SipForm(
                    start = LocalDate.parse(p.startDate), holdingId = p.holdingId,
                    amountText = com.moneymatters.app.core.money.Paise(p.amountPaise).plainRupees(), dayText = p.dayOfMonth.toString(),
                    accountId = p.fromAccountId, editingPlanId = planId,
                )
                onReady()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't open that SIP."
            }
        }
    }

    /** Units held, as shown on the sell form. */
    suspend fun unitsHeldLabel(holdingId: String) = formatUnits(invest.unitsHeld(holdingId))
}
