package com.moneymatters.app.core.invest

import androidx.room.withTransaction
import com.moneymatters.app.core.ledger.AccountKind
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.ledger.PostingLine
import com.moneymatters.app.core.ledger.TransactionTags
import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.impliedPrice
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Buys and sells for stocks and mutual funds. Each writes one balanced ledger entry through
 * [LedgerRepository] and the lot bookkeeping in the same database transaction, so the books and the
 * lots cannot drift apart. A trade's amount is the broker's net rupee figure; units and price are detail.
 */
class InvestRepository(
    private val database: MoneyMattersDatabase,
    private val ledger: LedgerRepository,
    private val rules: CapitalGainsRules,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao get() = database.investDao()

    /** Every holding, lot, sale and price at one moment, re-read after any investment write. */
    fun snapshot(): Flow<InvestSnapshot> =
        database.invalidationTracker.createFlow("holdings", "lots", "lot_consumptions", "prices", "corporate_actions", "pending_allotments", "fixed_deposits", "sip_plans", "sip_skips", "price_fetch_log").map {
            database.withTransaction {
                InvestSnapshot(dao.getAllHoldings(), dao.getAllLots(), dao.getAllConsumptions(), dao.getAllPrices(), dao.getAllCorporateActions(), dao.getAllPending(), dao.getAllFixedDeposits(), dao.getAllSipPlans(), dao.getAllSipSkips(), dao.getAllFetchLog())
            }
        }

    /**
     * Finds a holding by ISIN, then symbol (then, for a fund with no symbol, by its name), or creates it
     * with its own Investment-kind asset account. The account is named for the company or fund when
     * given, with the symbol in brackets for a stock.
     */
    suspend fun findOrCreateHolding(
        instrument: Instrument,
        symbol: String?,
        isin: String? = null,
        name: String? = null,
        fundClass: FundClass? = null,
        exitLoadDays: Int? = null,
        exitLoadBps: Int? = null,
    ): String = ledger.inTransaction {
        require(!symbol.isNullOrBlank() || !name.isNullOrBlank()) { "A holding needs a symbol or a name" }
        val accountName = when {
            name.isNullOrBlank() -> symbol!!
            instrument == Instrument.Stock && !symbol.isNullOrBlank() && !name.equals(symbol, ignoreCase = true) -> "$name ($symbol)"
            else -> name
        }
        val existing = isin?.let { dao.holdingByIsin(it) }
            ?: symbol?.takeIf { it.isNotBlank() }?.let { dao.holdingBySymbol(it) }
            ?: ledger.accountIdByName(accountName)?.let { dao.holdingForAccount(it) }
        if (existing != null) return@inTransaction existing.id
        val accountId = ledger.createAccount(accountName, AccountType.Asset, kind = AccountKind.Investment)
        addHolding(accountId, instrument, isin = isin, symbol = symbol?.takeIf { it.isNotBlank() }, fundClass = fundClass, exitLoadDays = exitLoadDays, exitLoadBps = exitLoadBps)
    }

    /**
     * Rebuilds lots and past sales from a trade history, all or nothing. Trades settle against
     * [settlementAccountId] (for history, `Equity:Opening`, so no bank balance moves). Sales are replayed
     * in date order against FIFO lots; one that exceeds the units held on its date aborts the whole import.
     */
    suspend fun importTrades(rows: List<TradeRow>, settlementAccountId: String): Int = ledger.inTransaction {
        for (row in rows.inLedgerOrder()) {
            val holdingId = findOrCreateHolding(row.instrument, row.symbol, row.isin, row.name, row.fundClass)
            when (row.side) {
                TradeSide.Split -> { applyCorporateAction(holdingId, RestateKind.Split, row.units, row.date); continue }
                TradeSide.Bonus -> { applyCorporateAction(holdingId, RestateKind.Bonus, row.units, row.date); continue }
                else -> Unit
            }
            val price = impliedPrice(row.units, row.amount)
            when (row.side) {
                TradeSide.Buy -> buy(holdingId, row.date, row.units, price, row.amount, settlementAccountId, "Buy ${row.symbol}")
                TradeSide.Sell -> sell(holdingId, row.date, row.units, price, row.amount, settlementAccountId, "Sell ${row.symbol}")
                TradeSide.Split, TradeSide.Bonus -> Unit
            }
        }
        rows.size
    }

    suspend fun addHolding(
        accountId: String,
        instrument: Instrument,
        isin: String? = null,
        symbol: String? = null,
        fundClass: FundClass? = null,
        exitLoadDays: Int? = null,
        exitLoadBps: Int? = null,
    ): String {
        val id = newId()
        dao.insertHolding(HoldingEntity(id, accountId, instrument, isin, symbol, fundClass, exitLoadDays, exitLoadBps))
        return id
    }

    /** (symbol, holding id) for every holding that has a symbol. */
    suspend fun holdingSymbols(): List<Pair<String, String>> = dao.getAllHoldings().mapNotNull { h -> h.symbol?.let { it to h.id } }

    /**
     * Records a dividend: the net [amount] lands in [toAccountId], any [tds] withheld becomes a
     * receivable to claim at tax time, and the gross is income. Counts towards the holding's return.
     */
    suspend fun recordDividend(
        holdingId: String,
        date: LocalDate,
        amount: Paise,
        tds: Paise,
        toAccountId: String,
        payee: String,
    ): String {
        require(amount.value > 0) { "A dividend's net amount must be positive" }
        require(tds.value >= 0) { "TDS can't be negative" }
        requireHolding(holdingId)
        return ledger.inTransaction {
            val income = ledger.createAccountIfAbsent("Income:Dividends", AccountType.Income)
            val postings = mutableListOf(PostingLine(toAccountId, debit = amount), PostingLine(income, credit = amount + tds))
            if (tds.value > 0) postings += PostingLine(ledger.createAccountIfAbsent(TDS_RECEIVABLE, AccountType.Asset), debit = tds)
            val id = ledger.postTransaction(date, payee, postings)
            dao.insertCorporateAction(CorporateActionEntity(newId(), holdingId, CorporateActionKind.Dividend, date.toString(), amountPaise = (amount + tds).value, transactionId = id))
            id
        }
    }

    /**
     * Records a split or bonus as a balanced entry that moves no money (the holding's cost is debited
     * and credited together) but carries the unit change, with the lots restated beside it. See [restate].
     * Trades dated before it can no longer be entered, since their units would be in the wrong terms.
     */
    suspend fun applyCorporateAction(holdingId: String, kind: RestateKind, ratio: Units, date: LocalDate): String {
        val holding = requireHolding(holdingId)
        return ledger.inTransaction {
            val plan = restate(openLots(holdingId), kind, ratio, date)
            val held = openLots(holdingId).filter { !it.acquiredOn.isAfter(date) && it.remainingUnits.micro > 0 }
            val cost = Paise(held.sumOf { it.remainingCost.value })
            check(cost.value > 0) { "Can't restate a holding with no cost" }
            val unitsAfter = Units(plan.created.sumOf { it.units.micro } + if (kind == RestateKind.Bonus) held.sumOf { it.remainingUnits.micro } else 0)
            val unitsBefore = Units(held.sumOf { it.remainingUnits.micro })
            val symbol = holding.symbol ?: "holding"
            val label = if (kind == RestateKind.Split) "Split $symbol ×${formatUnits(ratio)}" else "Bonus $symbol +${formatUnits(ratio)} per unit"
            val id = ledger.postTransaction(
                date, label,
                listOf(
                    PostingLine(holding.accountId, debit = cost, units = unitsAfter),
                    PostingLine(holding.accountId, credit = cost, units = unitsBefore),
                ),
            )
            dao.insertConsumptions(plan.consumed.map { LotConsumptionEntity(lotId = it.lotId, saleTransactionId = id, units = it.units.micro, costPaise = it.cost.value) })
            plan.created.forEach { dao.insertLot(LotEntity(newId(), holdingId, it.acquiredOn.toString(), it.units.micro, it.cost.value, sourceTransactionId = id)) }
            dao.insertCorporateAction(
                CorporateActionEntity(newId(), holdingId, if (kind == RestateKind.Split) CorporateActionKind.Split else CorporateActionKind.Bonus, date.toString(), ratioMicro = ratio.micro, transactionId = id),
            )
            id
        }
    }

    private suspend fun requireNoLaterRestatement(holdingId: String, date: LocalDate) {
        require(dao.restatementsAfter(holdingId, date.toString()) == 0) {
            "A split or bonus is recorded after $date; reverse it first, or enter the trade in post-action units"
        }
    }

    /** Lots that still have units unsold, oldest first, with what is left of each. */
    suspend fun openLots(holdingId: String): List<OpenLot> {
        val used = dao.consumptionsForHolding(holdingId).groupBy { it.lotId }
        return dao.lotsForHolding(holdingId).map { lot ->
            val consumed = used[lot.id].orEmpty()
            OpenLot(
                id = lot.id,
                acquiredOn = LocalDate.parse(lot.acquiredOn),
                remainingUnits = Units(lot.units - consumed.sumOf { it.units }),
                remainingCost = Paise(lot.costPaise - consumed.sumOf { it.costPaise }),
                lockUntil = lot.lockUntil?.let(LocalDate::parse),
            )
        }.filter { it.remainingUnits.micro > 0 }
    }

    suspend fun unitsHeld(holdingId: String): Units = openLots(holdingId).fold(Units.Zero) { acc, lot -> acc + lot.remainingUnits }

    /** Pays [amount] (net of every charge) from [fromAccountId] for [units] at [price], and opens a lot for them. */
    suspend fun buy(
        holdingId: String,
        date: LocalDate,
        units: Units,
        price: UnitPrice,
        amount: Paise,
        fromAccountId: String,
        payee: String,
        lockUntil: LocalDate? = null,
        memo: String? = null,
        tags: TransactionTags = TransactionTags(),
    ): String {
        require(units.micro > 0) { "Units bought must be positive" }
        val holding = requireHolding(holdingId)
        requireNoLaterRestatement(holdingId, date)
        return ledger.inTransaction {
            val id = ledger.postTransaction(
                date, payee,
                listOf(
                    PostingLine(holding.accountId, debit = amount, units = units, unitPrice = price),
                    PostingLine(fromAccountId, credit = amount),
                ),
                memo = memo, tags = tags,
            )
            dao.insertLot(LotEntity(newId(), holdingId, date.toString(), units.micro, amount.value, (lockUntil ?: defaultLock(holding, date))?.toString(), sourceTransactionId = id))
            id
        }
    }

    /**
     * Sells [units] first-in-first-out from lots bought on or before [date], paying [proceeds]
     * (net of every charge) into [toAccountId]. The gain or loss is posted to a capital-gains account
     * per term, split by each lot's own holding period, which comes from the tax rules for the holding's
     * kind. Locked units (ELSS) can't be sold, and debt fund units bought after the cutoff are slab-taxed.
     */
    suspend fun sell(
        holdingId: String,
        date: LocalDate,
        units: Units,
        price: UnitPrice,
        proceeds: Paise,
        toAccountId: String,
        payee: String,
        memo: String? = null,
        tags: TransactionTags = TransactionTags(),
    ): String {
        require(proceeds.value > 0) { "Sale proceeds must be positive" }
        val holding = requireHolding(holdingId)
        requireNoLaterRestatement(holdingId, date)
        return ledger.inTransaction {
            val bought = openLots(holdingId).filter { !it.acquiredOn.isAfter(date) }
            val eligible = bought.filter { it.lockUntil == null || !it.lockUntil.isAfter(date) }
            if (eligible.sumOf { it.remainingUnits.micro } < units.micro && bought.sumOf { it.remainingUnits.micro } >= units.micro) {
                val next = bought.mapNotNull { it.lockUntil }.filter { it.isAfter(date) }.min()
                throw LockedUnitsException("Some of those units are locked until $next")
            }
            val consumed = allocateFifo(eligible, units)
            val cost = Paise(consumed.sumOf { it.cost.value })
            val acquired = eligible.associate { it.id to it.acquiredOn }
            val rule = if (holding.isEquityOriented) rules.listedEquity(date) else rules.other(date)
            val slabFrom = if (holding.fundClass == FundClass.Debt) rules.debtSlabFrom(date) else null
            val buckets = splitGain(proceeds, consumed.map { DatedConsumption(it, acquired.getValue(it.lotId)) }, date, rule, slabFrom)

            val postings = mutableListOf(
                PostingLine(toAccountId, debit = proceeds),
                PostingLine(holding.accountId, credit = cost, units = units, unitPrice = price),
            )
            for (bucket in buckets) {
                val gainsAccount = ledger.createAccountIfAbsent(gainAccountName(holding, bucket.term), AccountType.Income)
                postings += if (bucket.gain.value > 0) PostingLine(gainsAccount, credit = bucket.gain) else PostingLine(gainsAccount, debit = -bucket.gain)
            }
            val id = ledger.postTransaction(date, payee, postings, memo = memo, tags = tags)
            dao.insertConsumptions(consumed.map { LotConsumptionEntity(lotId = it.lotId, saleTransactionId = id, units = it.units.micro, costPaise = it.cost.value) })
            id
        }
    }

    // ---- Fixed deposits ----

    /**
     * Opens a deposit as its own asset account. The [principal] leaves [fromAccountId] (for an
     * already-running deposit, pass `Equity:Opening` so no bank balance moves). Interest is booked later by [accrueInterest].
     */
    suspend fun openDeposit(
        name: String,
        principal: Paise,
        rateBps: Int,
        compounding: Compounding,
        start: LocalDate,
        maturity: LocalDate,
        fromAccountId: String,
        autoRenew: Boolean = false,
        penaltyBps: Int = 0,
    ): String {
        require(principal.value > 0) { "The principal must be positive" }
        require(rateBps > 0) { "The rate must be positive" }
        require(maturity.isAfter(start)) { "A deposit must mature after it starts" }
        require(name.isNotBlank()) { "A deposit needs a name" }
        return ledger.inTransaction {
            val accountId = ledger.createAccount(name.trim(), AccountType.Asset, kind = AccountKind.FixedDeposit)
            val txn = ledger.postTransaction(start, "Open $name", listOf(PostingLine(accountId, debit = principal), PostingLine(fromAccountId, credit = principal)))
            dao.insertFixedDeposit(FixedDepositEntity(accountId, principal.value, rateBps, compounding, start.toString(), maturity.toString(), autoRenew, penaltyBps, openTransactionId = txn))
            accountId
        }
    }

    /**
     * Books the interest earned to [asOf] (capped at maturity) that the ledger doesn't hold yet, as income.
     * A deposit's posted interest is its balance above principal, so this is always the exact top-up.
     */
    suspend fun accrueInterest(depositAccountId: String, asOf: LocalDate): Paise = ledger.inTransaction {
        val deposit = requireOpenDeposit(depositAccountId)
        val valuation = depositValuation(deposit, ledger.accountBalance(depositAccountId), asOf)
        postInterestAdjustment(deposit, valuation.accruedInterest - valuation.postedInterest, asOf)
    }

    /**
     * Pays a deposit out on [date]: at maturity, or early at the penalty rate if [date] is before it. The
     * interest is topped up (or trimmed, after an early break) to what is owed, any [tds] withheld becomes a
     * receivable, and the rest goes to [toAccountId], or, if [renew], straight into a new deposit of the same term.
     * Returns what was paid out.
     */
    suspend fun closeDeposit(depositAccountId: String, date: LocalDate, toAccountId: String?, tds: Paise = Paise.Zero, renew: Boolean = false): Paise {
        require(tds.value >= 0) { "TDS can't be negative" }
        require(renew || toAccountId != null) { "Choose where the money goes" }
        return ledger.inTransaction {
            val deposit = requireOpenDeposit(depositAccountId)
            val start = LocalDate.parse(deposit.startDate)
            val maturity = LocalDate.parse(deposit.maturityDate)
            require(date.isAfter(start)) { "A deposit can't be closed before it starts" }
            val principal = Paise(deposit.principalPaise)
            val posted = ledger.accountBalance(depositAccountId) - principal
            val owed = if (date.isBefore(maturity)) {
                breakValue(principal, deposit.rateBps, deposit.compounding, start, maturity, date, deposit.penaltyBps).proceeds - principal
            } else {
                accruedInterest(principal, deposit.rateBps, deposit.compounding, start, date, maturity)
            }
            postInterestAdjustment(deposit, owed - posted, date)
            val balance = principal + owed
            val payout = balance - tds
            require(payout.value > 0) { "TDS can't be as much as the deposit" }
            val name = requireNotNull(database.ledgerDao().getAccount(depositAccountId)).name
            val postings = mutableListOf(PostingLine(depositAccountId, credit = balance))
            var renewedInto: String? = null
            if (renew) {
                renewedInto = ledger.createAccount("$name (renewed ${date})", AccountType.Asset, kind = AccountKind.FixedDeposit)
                postings += PostingLine(renewedInto, debit = payout)
            } else {
                postings += PostingLine(toAccountId!!, debit = payout)
            }
            if (tds.value > 0) postings += PostingLine(ledger.createAccountIfAbsent(TDS_RECEIVABLE, AccountType.Asset), debit = tds)
            val txn = ledger.postTransaction(date, if (date.isBefore(maturity)) "Broke $name early" else "Matured $name", postings)
            dao.updateFixedDeposit(deposit.copy(closedOn = date.toString(), payoutPaise = payout.value, closeTransactionId = txn))
            if (renewedInto != null) {
                val term = ChronoUnit.DAYS.between(start, maturity)
                dao.insertFixedDeposit(
                    FixedDepositEntity(renewedInto, payout.value, deposit.rateBps, deposit.compounding, date.toString(), date.plusDays(term).toString(), deposit.autoRenew, deposit.penaltyBps, openTransactionId = txn),
                )
            }
            payout
        }
    }

    private suspend fun requireOpenDeposit(accountId: String): FixedDepositEntity {
        val deposit = dao.getFixedDeposit(accountId) ?: throw NoSuchElementException("No fixed deposit on account $accountId")
        check(deposit.closedOn == null) { "That deposit is already closed" }
        return deposit
    }

    /** Positive [delta] is more interest earned (income); negative takes back interest booked at a higher rate. Zero posts nothing. */
    private suspend fun postInterestAdjustment(deposit: FixedDepositEntity, delta: Paise, date: LocalDate): Paise {
        if (delta.value == 0L) return Paise.Zero
        val income = ledger.createAccountIfAbsent("Income:Interest", AccountType.Income)
        val postings = if (delta.value > 0) {
            listOf(PostingLine(deposit.accountId, debit = delta), PostingLine(income, credit = delta))
        } else {
            listOf(PostingLine(deposit.accountId, credit = -delta), PostingLine(income, debit = -delta))
        }
        ledger.postTransaction(date, "Interest on FD", postings)
        return delta
    }

    // ---- SIPs ----

    /** Starts a monthly SIP into a fund. Instalments are proposed from [start]; each is confirmed with [payInstalment]. */
    suspend fun createSipPlan(holdingId: String, amount: Paise, dayOfMonth: Int, fromAccountId: String, start: LocalDate, end: LocalDate? = null): String {
        require(amount.value > 0) { "The SIP amount must be positive" }
        require(dayOfMonth in 1..31) { "Day of month must be 1 to 31" }
        require(end == null || end.isAfter(start)) { "A SIP must end after it starts" }
        check(requireHolding(holdingId).instrument == Instrument.MutualFund) { "A SIP goes into a mutual fund" }
        val id = newId()
        dao.insertSipPlan(SipPlanEntity(id, holdingId, amount.value, dayOfMonth, fromAccountId, start.toString(), end?.toString()))
        return id
    }

    /** Ends a SIP after [lastDate]; instalments due later are no longer proposed. */
    suspend fun stopSip(planId: String, lastDate: LocalDate) {
        val plan = dao.getSipPlan(planId) ?: throw NoSuchElementException("No SIP with id $planId")
        dao.updateSipPlan(plan.copy(endDate = lastDate.toString()))
    }

    /**
     * Confirms an instalment as paid: the money goes In Transit for the fund, ready to be allotted units.
     * [due] must be one of the plan's own dates and not already paid; a skipped instalment can still be paid later.
     * Returns the order's id.
     */
    suspend fun payInstalment(planId: String, due: LocalDate): String = ledger.inTransaction {
        val plan = dao.getSipPlan(planId) ?: throw NoSuchElementException("No SIP with id $planId")
        val end = plan.endDate?.let(LocalDate::parse)
        require(sipDueDates(LocalDate.parse(plan.startDate), plan.dayOfMonth, due).lastOrNull() == due && (end == null || !due.isAfter(end))) {
            "That isn't one of this SIP's due dates"
        }
        val alreadyPaid = dao.getAllPending().any { it.sipPlanId == planId && it.orderDate == due.toString() && it.cancelTransactionId == null }
        check(!alreadyPaid) { "That instalment is already paid" }
        val holding = requireHolding(plan.holdingId)
        val name = database.ledgerDao().getAccount(holding.accountId)?.name ?: "fund"
        orderFund(plan.holdingId, due, Paise(plan.amountPaise), plan.fromAccountId, "SIP $name", sipPlanId = planId)
    }

    /** Skips an instalment on purpose: it stops being proposed, and still counts as missed. */
    suspend fun skipInstalment(planId: String, due: LocalDate) {
        dao.getSipPlan(planId) ?: throw NoSuchElementException("No SIP with id $planId")
        dao.insertSipSkip(SipSkipEntity(planId, due.toString()))
    }

    // ---- Fund purchases waiting for units (In Transit) ----

    /** Sends [amount] for a fund purchase: it leaves [fromAccountId] and waits In Transit until [allot] gives it units. */
    suspend fun orderFund(holdingId: String, date: LocalDate, amount: Paise, fromAccountId: String, payee: String, sipPlanId: String? = null): String {
        require(amount.value > 0) { "The amount must be positive" }
        val holding = requireHolding(holdingId)
        check(holding.instrument == Instrument.MutualFund) { "Only fund purchases go through In Transit" }
        return ledger.inTransaction {
            val inTransit = ledger.createAccountIfAbsent(IN_TRANSIT, AccountType.Asset, kind = AccountKind.Investment)
            val txn = ledger.postTransaction(date, payee, listOf(PostingLine(inTransit, debit = amount), PostingLine(fromAccountId, credit = amount)))
            val id = newId()
            dao.insertPending(PendingAllotmentEntity(id, holdingId, date.toString(), amount.value, txn, sipPlanId = sipPlanId))
            id
        }
    }

    /** Turns an open order into units: the In Transit money becomes [units] of the fund at [price] (implied by default), on [date]. */
    suspend fun allot(pendingId: String, date: LocalDate, units: Units, price: UnitPrice = UnitPrice(0)): String {
        require(units.micro > 0) { "Units allotted must be positive" }
        val pending = dao.getPending(pendingId) ?: throw NoSuchElementException("No order with id $pendingId")
        check(pending.isOpen) { "That order has already been allotted or cancelled" }
        require(!date.isBefore(LocalDate.parse(pending.orderDate))) { "Units can't be allotted before the order date" }
        val holding = requireHolding(pending.holdingId)
        requireNoLaterRestatement(holding.id, date)
        val amount = Paise(pending.amountPaise)
        val nav = if (price.tenThousandths > 0) price else impliedPrice(units, amount)
        return ledger.inTransaction {
            val inTransit = ledger.createAccountIfAbsent(IN_TRANSIT, AccountType.Asset, kind = AccountKind.Investment)
            val txn = ledger.postTransaction(
                date, "Allotment ${holding.symbol ?: ""}".trim(),
                listOf(PostingLine(holding.accountId, debit = amount, units = units, unitPrice = nav), PostingLine(inTransit, credit = amount)),
            )
            dao.insertLot(LotEntity(newId(), holding.id, date.toString(), units.micro, amount.value, defaultLock(holding, date)?.toString(), sourceTransactionId = txn))
            dao.updatePending(pending.copy(allotmentTransactionId = txn))
            txn
        }
    }

    /** Gives an open order's money back: it leaves In Transit and returns to the account it came from. */
    suspend fun cancelPending(pendingId: String, date: LocalDate): String {
        val pending = dao.getPending(pendingId) ?: throw NoSuchElementException("No order with id $pendingId")
        check(pending.isOpen) { "That order has already been allotted or cancelled" }
        return ledger.inTransaction {
            val inTransit = ledger.createAccountIfAbsent(IN_TRANSIT, AccountType.Asset, kind = AccountKind.Investment)
            val source = database.ledgerDao().getPostingsForTransaction(pending.orderTransactionId).first { it.creditPaise != null }.accountId
            val amount = Paise(pending.amountPaise)
            val txn = ledger.postTransaction(date, "Order cancelled", listOf(PostingLine(source, debit = amount), PostingLine(inTransit, credit = amount)))
            dao.updatePending(pending.copy(cancelTransactionId = txn))
            txn
        }
    }

    suspend fun recordPrice(holdingId: String, date: LocalDate, price: UnitPrice, source: String, status: PriceStatus, fetchedAt: Long = System.currentTimeMillis()) {
        requireHolding(holdingId)
        dao.insertPrice(PriceEntity(holdingId = holdingId, date = date.toString(), price = price.tenThousandths, source = source, status = status, fetchedAt = fetchedAt))
    }

    suspend fun latestPrice(holdingId: String): PriceEntity? = dao.latestPrice(holdingId)

    suspend fun holdings(): List<HoldingEntity> = dao.getAllHoldings()

    /** Each holding's display name (its account's name), by holding id. */
    suspend fun holdingNames(): Map<String, String> =
        dao.getAllHoldings().associate { h -> h.id to (database.ledgerDao().getAccount(h.accountId)?.name ?: h.symbol ?: h.id) }

    suspend fun logFetch(entry: PriceFetchLogEntity) = dao.insertFetchLog(entry)

    /**
     * Stores a downloaded price with the status it has earned (plan.html → "Price status badges"): [PriceStatus.Stale] if
     * its date is more than [STALE_DAYS] old (a frozen NAV, a suspended stock), [PriceStatus.Suspicious] if it moved
     * more than 20% from the previous price within ten days, [PriceStatus.Mismatch] (for it and the manual price
     * it disagrees with) if a manual price for the same day differs by more than 1%, else [PriceStatus.Verified].
     * A price of zero or less is never stored. Returns null if nothing was stored.
     */
    suspend fun ingestPrice(holdingId: String, date: LocalDate, price: UnitPrice, source: String, today: LocalDate, fetchedAt: Long): PriceStatus? = ledger.inTransaction {
        if (price.tenThousandths <= 0) return@inTransaction null
        requireHolding(holdingId)
        val sameDay = dao.pricesFor(holdingId, date.toString())
        if (sameDay.any { it.source == source }) return@inTransaction null
        val manual = sameDay.firstOrNull { it.source == MANUAL_SOURCE }
        val previous = dao.latestPriceBefore(holdingId, date.toString())
        val status = when {
            date.isBefore(today.minusDays(STALE_DAYS)) -> PriceStatus.Stale
            manual != null && differsBy(price.tenThousandths, manual.price, 0.01) -> PriceStatus.Mismatch
            previous != null && !LocalDate.parse(previous.date).isBefore(date.minusDays(10)) && differsBy(price.tenThousandths, previous.price, 0.20) -> PriceStatus.Suspicious
            else -> PriceStatus.Verified
        }
        if (status == PriceStatus.Mismatch && manual != null) dao.updatePrice(manual.copy(status = PriceStatus.Mismatch))
        dao.insertPrice(PriceEntity(holdingId = holdingId, date = date.toString(), price = price.tenThousandths, source = source, status = status, fetchedAt = fetchedAt))
        status
    }

    private fun differsBy(a: Long, b: Long, fraction: Double) = b > 0 && kotlin.math.abs(a - b).toDouble() / b > fraction

    /** You've looked at a stale, suspicious or mismatched price and accept it: it becomes a confirmed (manual) price. */
    suspend fun confirmPrice(priceId: Long) {
        val price = dao.getPrice(priceId) ?: throw NoSuchElementException("No price with id $priceId")
        dao.updatePrice(price.copy(status = PriceStatus.Manual))
    }

    /** Settles a manual price that disagreed with a download: keep the downloaded one (now verified) or your own (now confirmed). */
    suspend fun resolveMismatch(holdingId: String, date: LocalDate, keepDownloaded: Boolean) = ledger.inTransaction {
        val rows = dao.pricesFor(holdingId, date.toString()).filter { it.status == PriceStatus.Mismatch }
        check(rows.isNotEmpty()) { "There's no mismatch to resolve on $date" }
        val (mine, downloaded) = rows.partition { it.source == MANUAL_SOURCE }
        if (keepDownloaded) {
            mine.forEach { dao.deletePrice(it.id) }
            downloaded.forEach { dao.updatePrice(it.copy(status = PriceStatus.Verified)) }
        } else {
            downloaded.forEach { dao.deletePrice(it.id) }
            mine.forEach { dao.updatePrice(it.copy(status = PriceStatus.Manual)) }
        }
    }

    private suspend fun requireHolding(id: String) = dao.getHolding(id) ?: throw NoSuchElementException("No holding with id $id")

    private fun gainAccountName(holding: HoldingEntity, term: GainTerm): String = when (term) {
        GainTerm.Slab -> "Income:Capital Gains:Slab"
        else -> "Income:Capital Gains:${if (term == GainTerm.Short) "STCG" else "LTCG"}:${if (holding.isEquityOriented) "Equity" else "Other"}"
    }

    /** ELSS units are locked for three years from each purchase or allotment. */
    private fun defaultLock(holding: HoldingEntity, date: LocalDate): LocalDate? =
        if (holding.fundClass == FundClass.Elss) date.plusYears(ELSS_LOCK_YEARS) else null
}

/** Everything the Invest tab reads, in one consistent view. */
data class InvestSnapshot(
    val holdings: List<HoldingEntity>,
    val lots: List<LotEntity>,
    val consumptions: List<LotConsumptionEntity>,
    val prices: List<PriceEntity>,
    val corporateActions: List<CorporateActionEntity> = emptyList(),
    val pending: List<PendingAllotmentEntity> = emptyList(),
    val fixedDeposits: List<FixedDepositEntity> = emptyList(),
    val sipPlans: List<SipPlanEntity> = emptyList(),
    val sipSkips: List<SipSkipEntity> = emptyList(),
    val fetchLog: List<PriceFetchLogEntity> = emptyList(),
) {
    companion object { val Empty = InvestSnapshot(emptyList(), emptyList(), emptyList(), emptyList()) }
}

/** Where tax withheld at source waits until it is claimed (plan.html: TDS receivable). */
const val TDS_RECEIVABLE = "Assets:TDS Receivable"

/** Where fund purchases wait between payment and allotment (plan.html: "SIP via In Transit"). */
const val IN_TRANSIT = "Assets:Investments:In Transit"

private const val ELSS_LOCK_YEARS = 3L

/** A price older than this many days is shown as stale however it was obtained (the weekly download leaves a week's gap). */
const val STALE_DAYS = 8L

/** The `source` of a price typed in by hand. */
const val MANUAL_SOURCE = "manual"
