# Existing Invest tab and investment-tracking features (Money Matters codebase study)

Scope: read-only study of /home/user/money-matters on branch `claude/optimistic-newton-eb7ox6` (HEAD ff5a85a), 29 Oct 2026. No web research. All "sources" are repo files with line numbers (paths relative to the repo root). `docs/plan.html` line numbers refer to the raw HTML file. `docs/invest-plan.md` (the Invest tab build plan) was also found and is cited heavily, because it records what was actually built.

## 1. What docs/plan.html (and docs/invest-plan.md) say about Invest, and which decisions constrain a recommendation engine

### Takeaway
The plan says the app runs on one Android device for one user, works offline, and has no backend. The only network traffic is a weekly download of public prices. AI may only see aggregates, writes narrative only and never does the math. Tax output is "estimates only, not filing or advice". Investments are limited to stocks, MFs, FDs and SIPs (bonds were added later). The plan already names several advisory-style features: allocation targets with a glide path, rebalancing with new money first, concentration limits, idle-cash nudges and gains harvesting. These are the hooks a recommender would build on. It would have to stay rules-based, on-device and non-advisory.

### Cited Findings
**Platform and privacy decisions (quoted)**
- "Platform: Android only, single user, offline-first, no backend" — [docs/plan.html:163](docs/plan.html)
- "Tax: Tracked, local to Indian law; estimates only, not filing or advice" — [docs/plan.html:176](docs/plan.html); also "Estimates and records for your own planning and for sharing with a CA. The app doesn't file returns or give tax advice." — [docs/plan.html:426](docs/plan.html)
- Tech stack: "Background: WorkManager — Recurring entries, reminders, weekly price download, insight runs, auto-backup"; "Backup: Encrypted export to file / Google Drive — No server required"; "AI: Claude API (small model), user's own key in Keystore — Weekly summary only; aggregates only" — [docs/plan.html:194-198](docs/plan.html)
- Database: "Room (SQLite) + SQLCipher — Encrypted, offline, relational; immutability enforced with SQL triggers" — [docs/plan.html:193](docs/plan.html)
- Insights pipeline: "Ledger → Rules engine (on device, deterministic) → Facts JSON → AI → Weekly summary". "AI receives only computed aggregates, never the raw ledger or account numbers, and writes the narrative. It never does the math." "Payload preview: a screen shows exactly what would be sent. Opt-in; the rules-only summary works without AI." "one weekly summary, plus immediate alerts only for urgent items. Dismissing a type of alert repeatedly mutes it." — [docs/plan.html:432-437](docs/plan.html)
- Rules listed for that engine include "allocation drift, goal shortfall", plus runway change and others — [docs/plan.html:434](docs/plan.html)

**Investment scope and data decisions**
- "Investments: Stocks, mutual funds (lump sum and SIP) and FDs only. Build order: stocks, then MFs, then FDs, then SIPs" — [docs/plan.html:169](docs/plan.html)
- "Net worth basis: Market value: holdings count at their latest price (with its status badge), while the ledger itself keeps cost until the month-end revaluation entry. EPF is tracked as an account but kept out of net worth" — [docs/plan.html:170](docs/plan.html)
- "Unrealized gains: Posted to Equity:Unrealized Gains, so they never touch the income statement" — [docs/plan.html:171](docs/plan.html)
- "Prices: Weekly. AMFI (MF) and NSE (stocks) downloaded automatically; manual fallback; every price carries a status badge." — [docs/plan.html:172](docs/plan.html)
- Runway stress drops "Equity MF 30%, stocks 40%, cash/FD 0% (editable)"; Emergency fund "₹5,00,000, checked against 9 × essential monthly spend"; Wealth goal "₹1 Crore invested by age 30 (20 Nov 2029)" — [docs/plan.html:173-175](docs/plan.html)
- Data entry roadmap: "Manual first → statement import → SMS parsing (sideloaded APK)" — [docs/plan.html:178](docs/plan.html)
- Investment model: "Every purchase is a lot (date, units, cost including stamp duty and brokerage, lock-in date, 31 Jan 2018 value for grandfathering). Sales use lots FIFO." Also "Trade entry: one net rupee amount per trade" and "Investing is not spending. Savings rate and investment rate are reported separately." — [docs/plan.html:320-325](docs/plan.html)
- Per-instrument tracking table: stocks (lots, dividends with TDS, bonus/split, short vs long per lot); MFs (In Transit for SIPs, NAV allotted T+1, equity/debt/hybrid, ELSS 3-year lock per instalment, exit-load window, "debt funds bought after 1 Apr 2023 always taxed at slab rate"); FDs (principal, rate, compounding, start/maturity, monthly accrued interest, TDS, break penalty, auto-renewal) — [docs/plan.html:328-331](docs/plan.html)
- Out of scope: "Trading: intraday / F&O is out of scope for now ... Delivery-based stocks only." — [docs/plan.html:713](docs/plan.html)

**Portfolio features that already lean towards recommendations (quoted)**
- "XIRR per holding, per goal and for the portfolio, from the ledger's own cash flows" — [docs/plan.html:347](docs/plan.html)
- "Actual vs target allocation (equity / debt), drift alert at ±5%. Rebalance with new money first; selling comes with a tax estimate." — [docs/plan.html:348](docs/plan.html)
- "Concentration limit: no single stock above 10% of equity (editable); overlapping funds flagged" — [docs/plan.html:349](docs/plan.html)
- "Insights: idle cash above the emergency target, FDs maturing soon, lock-ins ending" — [docs/plan.html:350](docs/plan.html)
- Capital gains in Tax: "₹1.25L equity LTCG exemption tracker, gains-harvesting nudge in Feb/Mar, losses carried forward up to 8 years" — [docs/plan.html:421](docs/plan.html)
- "Investment reasons on every buy and sell (goal, thesis, holding period), with a reminder to revisit later." and "Missed SIPs are flagged with their effect on the goal date." — [docs/plan.html:311-312](docs/plan.html)

**Goals and allocation from goals**
- Emergency fund: separate from the wealth goal and held in savings and FDs. Checked against 9 × essential spend. "Suggested FD ladder (e.g. 5 × ₹1L)". "Until it's funded, surplus goes here first by default (can be turned off)." — [docs/plan.html:380-385](docs/plan.html)
- ₹1 Cr by 30: deadline 20 Nov 2029. "Measured on: market value of stocks, MFs and FDs, excluding the emergency fund". "Monthly investable amount: X, not yet known. The app works it out from 2–3 months of actual data (income − essentials − wants − sinking funds − emergency-fund top-up)." — [docs/plan.html:387-392](docs/plan.html)
- Required SIP table: 7% ≈ ₹2,44,000/mo, 10% ≈ ₹2,33,000/mo. The goal screen shows "required SIP vs X, the projected value on 20 Nov 2029, the shortfall, and a 'markets fall 30% that year' line", and "the horizon rule suggests starting balanced and gliding toward debt" — [docs/plan.html:395-399](docs/plan.html)
- "Allocation from goals (bottom-up)": Emergency 100% FD/liquid; under 3 years mostly or fully debt; 3–7 years 40–60% equity; 7+ years 70–85% equity. "The overall target is the sum of each goal's allocation. The app suggests a glide path as deadlines approach, and you set the final percentages." — [docs/plan.html:400-408](docs/plan.html)
- Open item: "Monthly investable amount X: to be measured from 2–3 months of tracked data." — [docs/plan.html:712](docs/plan.html)

**Leeway and runway (inputs for "surplus to invest")**
- "Safe to spend = liquid balance − bills, EMIs, card dues and SIPs due before next payday − this period's savings / sinking-fund target − buffer"; "Daily allowance = Safe to spend ÷ days until next payday" — [docs/plan.html:283-287](docs/plan.html)
- Runway ladder: "Liquidation value = Market value × (1 − stress drop) − exit costs − tax if sold". Steps: Cash, then + FDs (break penalty), then + Markets (30% / 40% drop, exit load, tax per lot) — [docs/plan.html:290-297](docs/plan.html)

**Pages and roadmap**
- P13 Portfolio, P14 Holding detail and P15 Prices are tagged Phase 2 — [docs/plan.html:591-616](docs/plan.html). P16 Goals (required SIP vs X, glide path, goal-level XIRR, "allocation suggested from horizon") is Phase 3 — [docs/plan.html:621-627](docs/plan.html). P19 Insights is Phase 4 — [docs/plan.html:654-659](docs/plan.html). P20 Tax, including "harvesting suggestions", is Phase 3 — [docs/plan.html:664-672](docs/plan.html)
- Roadmap: Phase 2 "stocks/MFs/FDs, weekly prices"; Phase 3 "Leeway, runway ladder, impact prompt, goals, sinking funds, income buffer, tax"; Phase 4 "Rules engine, alerts, AI weekly summary, weekly review"; Phase 5 "Statement and CAS import, then SMS / notification parsing" — [docs/plan.html:700-704](docs/plan.html)
- Bottom nav: "Invest: Portfolio, holdings, prices" — [docs/plan.html:449](docs/plan.html)

**docs/invest-plan.md (build plan, status of slices)**
- Slices 0–6 are marked Done: engine and schema; stocks with dividends, TDS and split/bonus; MFs; FDs; SIPs; prices; analytics and tax. "Not built: fund overlap, which needs each fund's underlying holdings and has no data source yet." — [docs/invest-plan.md:74-80](docs/invest-plan.md)
- Fixed-point rules: "Units are integers in micro-units (1 unit = 1,000,000) ... Prices are integers in 1/10,000 rupee ... No floats in stored values; XIRR is the only float and is display-only." — [docs/invest-plan.md:17](docs/invest-plan.md)
- "Market value is derived, never stored, until month-end close posts the revaluation entry" — [docs/invest-plan.md:21](docs/invest-plan.md). This was later superseded for close: "Holdings are not revalued with ledger entries: market value is computed when read, so the close records the price snapshot instead." — [docs/invest-plan.md:111](docs/invest-plan.md)
- The price download "only needs the INTERNET permission added for this slice; nothing else in the app uses the network." — [docs/invest-plan.md:104](docs/invest-plan.md)
- Capital-gains rates "are the Finance Act 2024 figures and are unverified against the Income-tax Act 2025"; the estimate "leaves out cess and surcharge" — [docs/invest-plan.md:118](docs/invest-plan.md)
- Not built: "fund overlap (no source for fund holdings), regime comparison, advance tax and deductions" — [docs/invest-plan.md:120](docs/invest-plan.md). Bonds: manual price only ("no feed covers bonds"); "Not built: bond maturity on the ladder, accrued interest, and redemption as a distinct entry" — [docs/invest-plan.md:124](docs/invest-plan.md)
- Deleting investment entries removes them instead of reversing them, a "deliberate exception" limited to Draft or Posted entries — [docs/invest-plan.md:128](docs/invest-plan.md)

### Inferences
- A recommendation engine fits the plan's existing "Rules engine (on device, deterministic) → Facts JSON → AI" pipeline (P19). Deterministic recommendations would be computed on the device. Any LLM layer would only phrase aggregates and would need opt-in, the user's own key and a payload preview.
- The "estimates only, not ... advice" framing and the "you set the final percentages" wording on allocation suggest the product deliberately keeps the user in control. An automation engine that places orders, or names specific securities to buy, would go beyond the decisions written so far and would need a new, recorded decision in plan.html (AGENTS.md requires plan.html updates when decisions change).
- The "rebalance with new money first" rule, the goal-horizon allocation table and the "surplus goes to emergency fund first" rule together already amount to a simple, specified allocation policy.

### Gaps
- plan.html has no section on recommendations, automated investing, broker integration, order placement or watchlists. Nothing settles whether such features are in scope.
- `docs/invest-plan.md` §1 ("The Invest tab shows 'not built yet'", "No ... WorkManager jobs or network code exist yet") [docs/invest-plan.md:7-12](docs/invest-plan.md) describes the state before the build and is out of date against the code. The "Done" markers in §6 are current.

## 2. What design/DESIGN.md and design/mockups/ show for the Invest screens

### Takeaway
There is no dedicated Invest-screen mockup. `design/mockups/` has only `dashboard.html`, which includes a Portfolio summary module, goal cards and an SIP-in-transit row. DESIGN.md defines the semantic colours for gains, losses and price badges, the price status bar, the five-tab nav including Invest, and a rule that colour is used only for meaning.

### Cited Findings
- The only mockup file is `design/mockups/dashboard.html` (directory listing; no invest.html or portfolio.html).
- Dashboard mockup Portfolio module: "Portfolio · Stocks & mutual funds · excl. emergency FDs" with an "Invest" pill and "XIRR 11.2%" — [design/mockups/dashboard.html:446-455](design/mockups/dashboard.html). CSS section "Portfolio" — [design/mockups/dashboard.html:228](design/mockups/dashboard.html)
- Mockup Goals module: "Emergency fund comes first", "Emergency fund ₹3,00,000 / ₹5,00,000", "✓ Covers 9 × essential spend"; "₹1 Crore by 30 · 3y 1m · 20 Nov 2029", "Invested now ₹60,900", "Required SIP ₹2,31,500 / mo", "Your SIP ₹5,000 / mo" — [design/mockups/dashboard.html:416-437](design/mockups/dashboard.html)
- Mockup bills row: "25 OCT SIP · Flexi Cap Fund From HDFC · auto ₹5,000 Reserved"; "⇄ SIP in transit · Awaiting NAV allotment" — [design/mockups/dashboard.html:405](design/mockups/dashboard.html), [design/mockups/dashboard.html:515](design/mockups/dashboard.html)
- Tone: "Assured, precise, discreet. Numbers are stated plainly and never dramatised." "Color is reserved for meaning (gain, loss, warning) and always appears small: a delta, a badge, a dot." — [design/DESIGN.md:175-177](design/DESIGN.md)
- Semantic colours: Positive = gains, inflows, ✅ verified price, on-track goals. Negative = losses, 🔴 failed download. Warning = 🟡 stale price, budget pace risk. Info = ✍️ manual price — [design/DESIGN.md:191-197](design/DESIGN.md). Champagne accent is for "Premium moments only, such as goal milestones", "No more than one champagne element per screen" — [design/DESIGN.md:189](design/DESIGN.md)
- "The price statuses (Verified, Manual, Mismatch, Stale, Failed, Suspicious) always use the same color and glyph across the app." — [design/DESIGN.md:317](design/DESIGN.md)
- "Price status bar: a full-width inner panel at the top of the Prices screen with one row per source (NSE, AMFI) ... with Retry and Change URL as secondary pills." — [design/DESIGN.md:348](design/DESIGN.md)
- Nav: "Five items: Home · Transactions · Accounts · Invest · More" — [design/DESIGN.md:340](design/DESIGN.md)
- Charts: "Semantic color in charts is limited to deltas, the over-budget part of a bar (negative), and the goal-reached marker (champagne)." — [design/DESIGN.md:337](design/DESIGN.md)

**What the shipped Invest screen renders** (from code, since there is no mockup)
- The header "Invest" and an empty state "No stocks yet · Tap + to add a stock, a mutual fund or a fixed deposit." — [app/src/main/java/com/moneymatters/app/feature/invest/InvestScreen.kt:91-97](app/src/main/java/com/moneymatters/app/feature/invest/InvestScreen.kt)
- Modules in order: an "In transit" list with Cancel and Allot pills (:105-117); SIPs with "+ New" (:130-140); "Fixed deposits" (:162); Allocation module (:178-179); a "Capital gains" row (:185); a "Prices" row with an "N to review" or "All set" badge (:197-203); then holdings grouped by instrument (:209-216). A summary module shows "Portfolio value", "Invested" and "XIRR" (:226-238) — [app/src/main/java/com/moneymatters/app/feature/invest/InvestScreen.kt](app/src/main/java/com/moneymatters/app/feature/invest/InvestScreen.kt)

### Inferences
- A recommendations surface on Invest would need a new design: either a mockup or a component added to `ui/components` (AGENTS.md rule). The existing analogues are the Allocation module (drift and concentration warnings) and the harvest hint on the Capital gains screen. By DESIGN.md's rules, recommendations would be neutral text with small semantic badges, not coloured cards.

### Gaps
- No Invest, Holding-detail or Prices mockup exists, so the Invest UI was designed in code.

## 3. Kotlin implementation: entities, DAOs, repositories, view models, screens; what is stored per holding; how prices and gains are computed

### Takeaway
The investment engine is fully implemented in `core/invest` (pure math plus Room) and `feature/invest` (state and screens). It stores holdings (ISIN, symbol, fund class, exit load, coupon, maturity), immutable lots with FIFO consumption rows, prices in 1/10,000 rupee with status badges, FDs, SIP plans, corporate actions, In-Transit orders and a price-fetch log. Prices come from manual entry plus an automatic weekly NSE and AMFI download. There is no AMFI scheme-code column and no instrument master: holdings exist only once the user enters a trade.

### Cited Findings
**Files**
- Core (19 files): AllocationPrefs, Analytics, CapitalGainsRules, Deposits, EquityTax, FixedDeposit, Gains, InvestDao, InvestEntities, InvestRepository (739 lines), Lots, PriceFeeds, PriceFetcher, PriceUpdater, PriceWorker, Sip, TradeSide, Valuation, Xirr — `app/src/main/java/com/moneymatters/app/core/invest/` (directory listing)
- Feature (16 files): ActionScreens, AddInvestmentScreen, AnalyticsScreens/State, DepositScreens, HoldingDetailScreen, HoldingEditScreen, InvestForms, InvestScreen, InvestState (495 lines), InvestViewModel (611 lines), PricesScreen/State, SipScreens/State, TradeScreens — `app/src/main/java/com/moneymatters/app/feature/invest/`
- Room DB version 12, with entities Account, Transaction, Posting, AuditLog, Holding, Lot, LotConsumption, Price, FixedDeposit, SipPlan, CorporateAction, PendingAllotment, SipSkip, PriceFetchLog, ClosedPeriod and CreditCard — [app/src/main/java/com/moneymatters/app/core/ledger/MoneyMattersDatabase.kt:67-73](app/src/main/java/com/moneymatters/app/core/ledger/MoneyMattersDatabase.kt). The exported schemas under `app/schemas/` go up to `11.json` (directory listing).
- Tests: about 270 `@Test` methods across 18 invest test files. Examples: InvestRepositoryTest 38, PriceUpdaterTest 28, InvestScreensTest 52, InvestViewModelTest 25 (grep count over `app/src/test/.../invest/`).

**Data stored**
- `Instrument { Stock, MutualFund, Bond }`; `FundClass { Equity, EquityHybrid, Hybrid, Debt, Elss }` — [InvestEntities.kt:10-21](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt)
- `HoldingEntity`: id, accountId (unique; one asset account per holding), instrument, `isin` ("Prices are matched by ISIN, never by name"), `symbol`, fundClass, exitLoadDays, exitLoadBps, couponBps, maturityDate (bonds) — [InvestEntities.kt:33-51](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt). **No AMFI scheme-code field, no exchange, no sector, no AMC, no expense ratio, no benchmark.**
- `LotEntity`: acquiredOn, units (micro), costPaise, lockUntil, fmv2018Paise, sourceTransactionId. "Never edited: what a sale uses is recorded in LotConsumptionEntity" — [InvestEntities.kt:53-73](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt); `LotConsumptionEntity` (lotId, saleTransactionId, units, costPaise) — [InvestEntities.kt:75-89](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt)
- `PriceEntity`: holdingId, date, price "In 1/10,000 rupee", source, status, fetchedAt — [InvestEntities.kt:91-105](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt). `PriceStatus { Verified, Manual, Mismatch, Stale, Failed, Suspicious }` — [InvestEntities.kt:30](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt)
- `FixedDepositEntity`: principalPaise, rateBps, compounding, startDate, maturityDate, autoRenew, penaltyBps, open/close transaction ids, closedOn, payoutPaise — [InvestEntities.kt:107-124](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt). `Compounding { Monthly(1), Quarterly(3), Simple(0) }` — [FixedDeposit.kt:11](app/src/main/java/com/moneymatters/app/core/invest/FixedDeposit.kt)
- `SipPlanEntity`: holdingId, amountPaise, dayOfMonth, fromAccountId, startDate, endDate, **`goalId`** (nullable; nothing populates a goals table because none exists) — [InvestEntities.kt:126-143](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt)
- `CorporateActionEntity` (Split, Bonus, Dividend, Coupon; ratioMicro; amountPaise; transactionId) — [InvestEntities.kt:145-161](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt); `PendingAllotmentEntity` (In Transit orders, with sipPlanId) — [InvestEntities.kt:163-187](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt); `SipSkipEntity` — [InvestEntities.kt:189-195](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt); `PriceFetchLogEntity` (source, ok, httpStatus, rowsParsed, rowsMatched, message, priceDate) — [InvestEntities.kt:197-212](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt)
- `PostingEntity` carries nullable `units` and `unitPrice` on the holding leg ("Detail only; money is the debit/credit") — [core/ledger/Entities.kt:60-79](app/src/main/java/com/moneymatters/app/core/ledger/Entities.kt). `AccountEntity` has `kind` (Bank, Cash, CreditCard, Loan, FixedDeposit, Investment) and `inNetWorth` — [core/ledger/Entities.kt:16-31](app/src/main/java/com/moneymatters/app/core/ledger/Entities.kt)
- `TransactionEntity` has memo, status, decision (Need, Want, Investment), plan (Planned, Unplanned) and paymentMethod. **There is no "investment reason / thesis / goal" field**, although plan.html asks for one — [core/ledger/Entities.kt:34-55](app/src/main/java/com/moneymatters/app/core/ledger/Entities.kt)
- Fixed-point types: `Units(micro)`, `UnitPrice(tenThousandths)`, and `marketValue(units, price)` as "the only place that converts the two", rounded half-up via BigInteger — [core/money/Units.kt:8-39](app/src/main/java/com/moneymatters/app/core/money/Units.kt)
- The equity allocation target is a single int percent in plain SharedPreferences (`allocation` file) — [AllocationPrefs.kt:5-16](app/src/main/java/com/moneymatters/app/core/invest/AllocationPrefs.kt), [MainActivity.kt:85](app/src/main/java/com/moneymatters/app/MainActivity.kt)

**Repository operations** ([InvestRepository.kt](app/src/main/java/com/moneymatters/app/core/invest/InvestRepository.kt))
- `snapshot(): Flow<InvestSnapshot>` re-reads all ten invest tables on any change, inside a DB transaction — :35-40. `InvestSnapshot` data class — :712-725
- `findOrCreateHolding` (by ISIN, then symbol, then name; creates an Investment-kind asset account) :47; `buy` (posts a balanced Dr holding with units and price, Cr cash entry, then inserts a lot, defaulting the ELSS lock to 3 years) :173-205; `sell` (FIFO over unlocked lots, gain posted per term to `Income:Capital Gains:{STCG|LTCG}:{Equity|Other}` or `...:Slab`) :208-250 and :697-700; `recordDividend` (dividends and coupons, with TDS) :94; `applyCorporateAction` :122; `openDeposit` / `accrueInterest` / `closeDeposit` (with renew) :256-334; SIP `createSipPlan` / `payInstalment` / `skipInstalment` :529-586; `orderFund` / `allot` / `cancelPending` (In Transit) :588-640; `recordPrice`, `ingestPrice`, `confirmPrice`, `resolveMismatch` :643-695; edit and delete of trades, holdings, deposits and SIPs :361-560
- `ingestPrice` status logic: Stale if older than `STALE_DAYS = 8`; Mismatch if a manual price for the same day differs by more than 1%; Suspicious if it moved more than 20% from a price within 10 days; zero or negative prices are never stored — :652-676, :736

**Prices / NAVs: how they are obtained**
- Manual entry: `PriceScreen` / `PriceForm` — [TradeScreens.kt:179-193](app/src/main/java/com/moneymatters/app/feature/invest/TradeScreens.kt), [InvestForms.kt:64](app/src/main/java/com/moneymatters/app/feature/invest/InvestForms.kt)
- Network fetch: `HttpPriceFetcher` uses `java.net.HttpURLConnection` with a browser User-Agent, an NSE Referer, 3 retries with exponential backoff on IO/429/5xx, a 1 s minimum gap, a 30 s timeout and a 40 MB cap — [PriceFetcher.kt:35-95](app/src/main/java/com/moneymatters/app/core/invest/PriceFetcher.kt)
- Default sources: NSE `https://nsearchives.nseindia.com/content/cm/BhavCopy_NSE_CM_0_0_0_{yyyyMMdd}_F_0000.csv.zip` and AMFI `https://www.amfiindia.com/spages/NAVAll.txt`. Both are user-editable (`PriceSourcePrefs`) — [PriceUpdater.kt:17-41](app/src/main/java/com/moneymatters/app/core/invest/PriceUpdater.kt)
- `PriceUpdater.refresh()` runs NSE then AMFI. For NSE it walks back up to 7 weekdays on 404 and matches only *held* stocks by ISIN, then by symbol. For AMFI it matches only *held* funds by ISIN; a fund without an ISIN is reported as "no ISIN saved". Every attempt is logged — [PriceUpdater.kt:70-172](app/src/main/java/com/moneymatters/app/core/invest/PriceUpdater.kt)
- Parsers: `parseAmfi` keeps schemeCode, ISINs, name, NAV and date per row. `parseNse` reads columns by header (TradDt, ISIN, TckrSymb, SctySrs, FinInstrmNm, ClsPric). `NseIndex` prefers series EQ > BE > BZ > SM > ST — [PriceFeeds.kt:14-92](app/src/main/java/com/moneymatters/app/core/invest/PriceFeeds.kt). The parsed files cover the **whole market** (every NSE stock and every AMFI scheme), but only matched rows for held instruments are stored; the rest is thrown away.
- Bonds: no feed; manual price only — [docs/invest-plan.md:124](docs/invest-plan.md)

**Gains, tax, XIRR and valuation math**
- FIFO `allocateFifo`, with pro-rata cost rounded half-up and the last consumption taking the remainder; `restate` for split and bonus — [Lots.kt:26-91](app/src/main/java/com/moneymatters/app/core/invest/Lots.kt)
- `gainTerm` (long-term only when held *more than* N months) and `splitGain` into Short / Long / Slab buckets — [Gains.kt:7-48](app/src/main/java/com/moneymatters/app/core/invest/Gains.kt)
- Versioned tax data: `CapitalGainsRules` loads `assets/tax/capital_gains.json`. Each entry has effectiveFrom, listedEquityLongTermMonths, otherLongTermMonths, debtFundSlabTaxedFrom, equity ST and LT bps, and the LT exemption in paise — [CapitalGainsRules.kt:13-62](app/src/main/java/com/moneymatters/app/core/invest/CapitalGainsRules.kt). The data has two entries, 2018-04-01 (15% / 10% / ₹1,00,000) and 2024-07-23 (20% / 12.5% / ₹1,25,000) — [app/src/main/assets/tax/capital_gains.json:1-7](app/src/main/assets/tax/capital_gains.json)
- `equityYears`: yearly set-off, 8-year loss carry-forward, exemption, estimated tax (no cess or surcharge) — [EquityTax.kt:6-77](app/src/main/java/com/moneymatters/app/core/invest/EquityTax.kt)
- `taxIfSoldToday` (oldest unlocked lots, latest price, today's rules, nothing written) and `capitalGainsRegister` — [Analytics.kt:68-143](app/src/main/java/com/moneymatters/app/core/invest/Analytics.kt)
- Allocation buckets `AssetClass { Equity, Debt, Other }`; `allocation()` counts open FDs as Debt and uses market value, or cost when unpriced; `drift()` alerts at `DRIFT_ALERT_POINTS = 5.0`; `concentrations()` flags a stock above `CONCENTRATION_LIMIT_PERCENT = 10` of equity — [Analytics.kt:10-66](app/src/main/java/com/moneymatters/app/core/invest/Analytics.kt). **These thresholds are constants in code, not user settings,** although plan.html calls the 10% limit "(editable)".
- `xirr()`: bisection on actual/365, display-only Double — [Xirr.kt:15-37](app/src/main/java/com/moneymatters/app/core/invest/Xirr.kt). `holdingFlows()` derives per-holding cash flows from ledger postings — [Valuation.kt:37-61](app/src/main/java/com/moneymatters/app/core/invest/Valuation.kt). `valuations()` gives remaining units and cost per holding at the latest price — [Valuation.kt:19-35](app/src/main/java/com/moneymatters/app/core/invest/Valuation.kt)
- FD math: `accruedInterest`, `maturityValue`, `breakValue` (penalty-free within 30 days of maturity), all in BigDecimal — [FixedDeposit.kt:18-73](app/src/main/java/com/moneymatters/app/core/invest/FixedDeposit.kt); `depositValuation` — [Deposits.kt:7-46](app/src/main/java/com/moneymatters/app/core/invest/Deposits.kt)
- SIP schedule: `sipDueDates`, `sipInstalments` (Paid, Skipped, Due, Missed, with `SIP_GRACE_DAYS = 3`), `nextSipDue` — [Sip.kt:6-61](app/src/main/java/com/moneymatters/app/core/invest/Sip.kt); `missedImpact` grows each missed instalment to the goal date — [feature/invest/SipState.kt:96-103](app/src/main/java/com/moneymatters/app/feature/invest/SipState.kt)

**View model and state**
- `buildInvest(ledger, invest, today, rules, goal, zone, targetEquityPercent)` is a pure function producing `InvestState`: holdings, cost, value, gain, gainPercent, portfolio XIRR (only when all holdings are priced), pending, deposits, SIPs, prices, analytics, gainsByYear, harvest, ladder and sipCalendar — [feature/invest/InvestState.kt:134-163](app/src/main/java/com/moneymatters/app/feature/invest/InvestState.kt), [feature/invest/InvestState.kt:222-290](app/src/main/java/com/moneymatters/app/feature/invest/InvestState.kt)
- `harvestRoom` gives this financial year's remaining LTCG exemption against the long-term gain available if sold today, as a hint only — [feature/invest/InvestState.kt:292-302](app/src/main/java/com/moneymatters/app/feature/invest/InvestState.kt)
- `InvestViewModel` combines `ledger.snapshot()`, `invest.snapshot()` and the target equity into state. `nudgeTarget(delta)` defaults to 60% when unset. Constructor dependencies: LedgerRepository, InvestRepository, CapitalGainsRules, today, goal, PriceUpdater, PriceSourcePrefs, AllocationPrefs — [feature/invest/InvestViewModel.kt:56-82](app/src/main/java/com/moneymatters/app/feature/invest/InvestViewModel.kt)
- Wiring in MainActivity: `goal = { profiles.profile()?.takeIf { it.croreByThirtyGoal }?.let { SipGoal(it.dateOfBirth.plusYears(30), 0.12) } }`, which hard-codes a 12% assumed return; `priceUpdater = PriceUpdater(investRepo, HttpPriceFetcher(), priceSourcePrefs::sources)` — [MainActivity.kt:89-93](app/src/main/java/com/moneymatters/app/MainActivity.kt)

### Inferences
- The lot, gain and tax engine is a strong base for tax-aware recommendations (which lots to sell, harvesting, rebalancing cost) because `taxIfSoldToday` and `splitGain` are pure, take a date and write nothing.
- Recommending *new* instruments (ones the user does not hold) is not supported by the data model. There is no instrument master table, no AMFI scheme-code column on holdings, and downloaded price files are filtered down to held instruments before storage.
- XIRR, drift and concentration all depend on prices. Unpriced holdings fall back to cost, and portfolio XIRR is null unless every holding is priced. A recommender would inherit these "as of last price" caveats and the badges.

### Gaps
- Not verified by running the app or the tests (read-only study). Behaviour is inferred from code and test names.
- The detailed contents of `HoldingDetailScreen.kt`, `DepositScreens.kt` and `SipScreens.kt` were skimmed via declarations only.

## 4. Networking, background work, notifications, external APIs, manifest permissions

### Takeaway
The app has exactly one network path (weekly NSE and AMFI price download via `HttpURLConnection`) and exactly one WorkManager job (`PriceWorker`). There are no notifications, no notification permission, no HTTP client library and no AI or Claude API client code. An AI settings store exists but is unused.

### Cited Findings
- Manifest permissions: only `USE_BIOMETRIC` and `INTERNET`, the latter commented "Weekly price download (AMFI NAVs, NSE closing prices). Everything else works offline." `android:allowBackup="false"`. Components: MainActivity plus three widget receivers; no services, no `POST_NOTIFICATIONS`, no `RECEIVE_BOOT_COMPLETED` — [app/src/main/AndroidManifest.xml:4-6](app/src/main/AndroidManifest.xml), [app/src/main/AndroidManifest.xml:8-59](app/src/main/AndroidManifest.xml)
- Dependencies: core-ktx, splashscreen, activity-compose, glance-appwidget, Compose BOM, Room, SQLCipher, security-crypto, `work-runtime-ktx` 2.11.2. **No Retrofit, OkHttp, Ktor, kotlinx-serialization or Hilt/Dagger** — [app/build.gradle.kts:73-92](app/build.gradle.kts), [gradle/libs.versions.toml:1-43](gradle/libs.versions.toml)
- A grep for HttpURLConnection, okhttp, retrofit, ktor, WorkManager, NotificationCompat, NotificationManager, AlarmManager or anthropic across `app/src/main/java` finds hits only in `core/invest/Price*.kt` and `MainActivity.kt:35,132` (the `PriceWorker.schedule` call).
- `PriceWorker` is a CoroutineWorker on a 7-day periodic schedule with its initial delay set to the next Saturday 09:00, requires a CONNECTED network, uses unique work `weekly-price-download` with policy KEEP, and retries only on "Couldn't connect" — [PriceWorker.kt:18-52](app/src/main/java/com/moneymatters/app/core/invest/PriceWorker.kt). It is scheduled on every launch — [MainActivity.kt:132](app/src/main/java/com/moneymatters/app/MainActivity.kt)
- `AiSettingsStore` holds an enabled flag, an API key and a payload-preview flag in encrypted prefs: "Not consumed until the AI weekly summary (P19, Phase 4) exists" — [feature/settings/AiSettingsStore.kt:7-38](app/src/main/java/com/moneymatters/app/feature/settings/AiSettingsStore.kt)
- Settings rows mark features as not yet active: Accountability ("Phase 3"), Runway ("coming in Phase 3"), AI ("not active until the weekly review ships (Phase 4)"), and Notifications ("Not active until bill, EMI and SIP reminders ship (Phase 2)") — [feature/settings/SettingsScreen.kt:91-114](app/src/main/java/com/moneymatters/app/feature/settings/SettingsScreen.kt). The Prices settings row still says "coming in Phase 2" — [feature/settings/SettingsScreen.kt:100](app/src/main/java/com/moneymatters/app/feature/settings/SettingsScreen.kt) — even though source URLs are already settable via `PriceSourcePrefs` and the Prices screen.
- `PriceWorker` builds its own `InvestRepository` from `LedgerDatabaseProvider.get(context)`, which shows that background jobs can reach the encrypted DB without the UI — [PriceWorker.kt:30-37](app/src/main/java/com/moneymatters/app/core/invest/PriceWorker.kt)

### Inferences
- Adding alerts or recommendation pushes needs new plumbing: a notification channel, the `POST_NOTIFICATIONS` runtime permission (Android 13+), and probably a second Worker or an extension of `PriceWorker` that runs analytics after each price refresh.
- Any new network source (corporate actions, fund portfolios for overlap, index data, a broker API) would widen a surface that is currently documented as "nothing else in the app uses the network". plan.html and the manifest comment would need updating.

### Gaps
- None found beyond the absence of these components.

## 5. Goal, budget, leeway and runway data a recommender could use

### Takeaway
Most of the planning inputs a recommender would need (goals, budgets, leeway, runway, recurring rules, investable surplus X) are **not yet in the database**. Goals exist only as two booleans in an encrypted profile, plus a DOB-derived date and a hard-coded 12% return. Leeway and runway exist as math helpers and sample data for a Phase-3 dashboard that is not yet wired to real data. The real inputs available today are: ledger balances by account kind, need/want/investment decision tags on transactions, the profile's monthly take-home and essential categories, SIP plans, FDs, and the single equity target.

### Cited Findings
- The DB has no goal, budget, recurring_rule, insight or tax_rule tables (the entities list in [MoneyMattersDatabase.kt:67-72](app/src/main/java/com/moneymatters/app/core/ledger/MoneyMattersDatabase.kt)), although plan.html's data model lists "recurring_rule, budget, goal, tax_rule (versioned per tax year), insight, price_fetch_log" — [docs/plan.html:224](docs/plan.html)
- `UserProfile`: dateOfBirth, monthStartDay, taxRegime (New/Old), monthlyTakeHome (Paise?), hasFreelanceIncome, essentialCategories (Set<String>), emergencyFundGoal (Boolean), croreByThirtyGoal (Boolean), stored in encrypted prefs `app_profile` — [core/profile/ProfileStore.kt:9-74](app/src/main/java/com/moneymatters/app/core/profile/ProfileStore.kt). There are no goal amounts, target dates or links to holdings beyond these booleans.
- The only goal object in Invest is `SipGoal(date, annualReturn)` — [feature/invest/SipState.kt:21-22](app/src/main/java/com/moneymatters/app/feature/invest/SipState.kt) — built as DOB + 30 years at 0.12 return — [MainActivity.kt:92](app/src/main/java/com/moneymatters/app/MainActivity.kt). `SipPlanEntity.goalId` exists but has no table to refer to — [InvestEntities.kt:142](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt)
- Home is the Phase-1 simple version: "The full dashboard ([DashboardScreen]: leeway, runway, budgets, goals) replaces it in Phase 3, once those numbers exist in the ledger." — [feature/dashboard/HomeScreen.kt:60-63](app/src/main/java/com/moneymatters/app/feature/dashboard/HomeScreen.kt). `DashboardScreen` is only called from a preview with `SampleDashboard.state` — [feature/dashboard/DashboardScreen.kt:146](app/src/main/java/com/moneymatters/app/feature/dashboard/DashboardScreen.kt); the app's tab 0 renders `HomeScreen` — [ui/MoneyMattersApp.kt:305](app/src/main/java/com/moneymatters/app/ui/MoneyMattersApp.kt)
- Reusable planning math already exists in `DashboardMath.kt`: `dailyAllowance`, `budgetPace`, `projectedValue`, `projectedRupees` (plotting only) and `requiredMonthlyContribution(target, start, months, annualReturn)` — [feature/dashboard/DashboardMath.kt:8-61](app/src/main/java/com/moneymatters/app/feature/dashboard/DashboardMath.kt)
- Transaction decision tags `DecisionKind { Need, Want, Investment }` and `PlanKind { Planned, Unplanned }` are stored per transaction — [core/ledger/Entities.kt:37-54](app/src/main/java/com/moneymatters/app/core/ledger/Entities.kt). Account kinds (Bank, Cash, CreditCard, Loan, FixedDeposit, Investment) and `inNetWorth` allow liquid cash to be separated from investments — [core/ledger/Entities.kt:16-31](app/src/main/java/com/moneymatters/app/core/ledger/Entities.kt)
- FD maturity ladder and break-today values are computed (`maturityLadder`, `DepositValuation.breakProceeds`) — [feature/invest/InvestState.kt:484](app/src/main/java/com/moneymatters/app/feature/invest/InvestState.kt), [Deposits.kt:12-27](app/src/main/java/com/moneymatters/app/core/invest/Deposits.kt)

### Inferences
- Two inputs are missing before a recommender could suggest how much to invest this month: the "monthly investable amount X" and the leeway "safe to spend" figure. Both are still plan items (Phase 3 and the open item at plan.html:712). They could be derived from what exists: monthly take-home or income postings, minus need-tagged expense postings, minus SIPs due, minus the emergency-fund top-up.
- The emergency-fund-first rule can be computed today: liquid Bank/Cash/FD balances against 9 × essential spend (from `essentialCategories` and expense postings). The ₹5L target itself is only a plan constant, not stored data.

### Gaps
- There is no stored goal target amount, goal-to-holding link, risk profile, budget, recurring rule or income-buffer data, so goal-based allocation (plan.html:400-408) has no persisted inputs yet.

## 6. Where an engine would plug in, what is missing, and the AGENTS.md rules it must respect

### Takeaway
The natural seam is a new pure module (for example `core/invest/advice` or `core/insights`) shaped like `Analytics.kt`. It would take `LedgerSnapshot`, `InvestSnapshot`, `CapitalGainsRules`, today and preferences, and return recommendation data classes. These would be surfaced through `buildInvest` / `InvestState` and a new `ui/components` module, run in the background by a Worker next to `PriceWorker`, and feed P19's Facts JSON. Missing pieces: an instrument master (AMFI scheme list and NSE symbols), stored goals, budgets and recurring rules, investable surplus, watchlists, notifications, corporate-action and fund-holdings data, and versioned data files for any new thresholds.

### Cited Findings
**Existing seams**
- Layering is `core/invest` ("pure, tested math") plus `feature/invest` ("state + screens"), "mirroring core/ledger and feature/books". "a separate InvestState is built from the same LedgerSnapshot plus the new tables so pages never disagree." — [docs/invest-plan.md:41](docs/invest-plan.md)
- Pure, date-parameterised functions ready to reuse: `allocation`, `drift`, `concentrations`, `taxIfSoldToday`, `capitalGainsRegister` — [Analytics.kt:38-143](app/src/main/java/com/moneymatters/app/core/invest/Analytics.kt); `equityYears` — [EquityTax.kt:33](app/src/main/java/com/moneymatters/app/core/invest/EquityTax.kt); `valuations`, `holdingFlows` — [Valuation.kt:20-61](app/src/main/java/com/moneymatters/app/core/invest/Valuation.kt); `requiredMonthlyContribution` — [DashboardMath.kt:54](app/src/main/java/com/moneymatters/app/feature/dashboard/DashboardMath.kt). invest-plan.md states that "the register and valuation functions are pure and take a date, so they can be called from those pages when they land" — [docs/invest-plan.md:80](docs/invest-plan.md)
- A single combined state flow: `InvestViewModel.state = combine(ledger.snapshot(), invest.snapshot(), targetEquity) { ... buildInvest(...) }` — [InvestViewModel.kt:76](app/src/main/java/com/moneymatters/app/feature/invest/InvestViewModel.kt)
- Background pattern: `PriceWorker` builds repositories itself from `LedgerDatabaseProvider` — [PriceWorker.kt:29-40](app/src/main/java/com/moneymatters/app/core/invest/PriceWorker.kt)
- The month-end close checklist already consumes `InvestSnapshot` (`buildClose(ledger, invest, ...)`) — [feature/close/CloseState.kt:38](app/src/main/java/com/moneymatters/app/feature/close/CloseState.kt)
- Write paths for "automation" already exist as repository calls: `buy`, `sell`, `orderFund`, `payInstalment`, `openDeposit`, `closeDeposit(renew = true)` — [InvestRepository.kt](app/src/main/java/com/moneymatters/app/core/invest/InvestRepository.kt) (:173, :208, :588, :566, :256, :295). These *record* trades in the user's books; none places a real order. There is no broker integration.
- SIP proposals "appear on opening the Invest tab until recurring rules (P11) land" — [docs/invest-plan.md:78](docs/invest-plan.md)

**Gaps an engine would need filled** (based on code that is absent)
- Instrument master or catalogue: no table. AMFI scheme code is parsed but not stored (`AmfiRow.schemeCode` in [PriceFeeds.kt:15-23](app/src/main/java/com/moneymatters/app/core/invest/PriceFeeds.kt) against no column in [InvestEntities.kt:38-51](app/src/main/java/com/moneymatters/app/core/invest/InvestEntities.kt)). Only held instruments are priced ([PriceUpdater.kt:97](app/src/main/java/com/moneymatters/app/core/invest/PriceUpdater.kt), [PriceUpdater.kt:135](app/src/main/java/com/moneymatters/app/core/invest/PriceUpdater.kt)).
- Price history depth: prices are weekly snapshots for held instruments only, from the first download or manual entry onward. There is no backfill and no index or benchmark series.
- Fund look-through and overlap: "no data source yet" — [docs/invest-plan.md:80](docs/invest-plan.md), [docs/invest-plan.md:120](docs/invest-plan.md)
- Watchlists, price alerts and notifications: no entities, no notification code, no permission (sections 3 and 4 above).
- Goals, budgets, recurring rules and investable X: not persisted (section 5 above).
- Investment reasons and thesis per trade (plan.html:311): no field in `TransactionEntity`, which has only memo, decision, plan and payment method ([core/ledger/Entities.kt:43-55](app/src/main/java/com/moneymatters/app/core/ledger/Entities.kt); `TransactionTags` at [core/ledger/LedgerRepository.kt:28](app/src/main/java/com/moneymatters/app/core/ledger/LedgerRepository.kt)).
- Thresholds in code: `DRIFT_ALERT_POINTS = 5.0` and `CONCENTRATION_LIMIT_PERCENT = 10` ([Analytics.kt:31-32](app/src/main/java/com/moneymatters/app/core/invest/Analytics.kt)), `STALE_DAYS = 8L` ([InvestRepository.kt:736](app/src/main/java/com/moneymatters/app/core/invest/InvestRepository.kt)), the 12% SIP-goal return ([MainActivity.kt:92](app/src/main/java/com/moneymatters/app/MainActivity.kt)), and `SIP_GRACE_DAYS = 3` ([Sip.kt:12](app/src/main/java/com/moneymatters/app/core/invest/Sip.kt)). The ELSS lock (`ELSS_LOCK_YEARS = 3L`, [InvestRepository.kt:733](app/src/main/java/com/moneymatters/app/core/invest/InvestRepository.kt)) is a tax rule hard-coded in code rather than versioned data.
- Tax-data verification: rates are the Finance Act 2024 figures, "unverified against the Income-tax Act 2025" — [docs/invest-plan.md:118](docs/invest-plan.md), [app/src/main/assets/tax/capital_gains.json:2](app/src/main/assets/tax/capital_gains.json); plan.html open item — [docs/plan.html:714](docs/plan.html)

**AGENTS.md non-negotiable rules any engine must respect** ([AGENTS.md](AGENTS.md), "Non-negotiable rules")
- "Every transaction balances (debits = credits). Store money as integer paise, never floats."
- "Entries in reconciled or closed months are immutable. Corrections go in as reversals or prior-period adjustments."
- "Tax rates, limits and section numbers live in versioned per-tax-year data, never in code."
- "Any change to ledger, close or tax logic needs tests that prove these rules still hold."
- "UI follows design/DESIGN.md: JetBrains Mono throughout, semantic colors only for meaning."
- "Use the tokens in ui/theme (MoneyTheme.colors/type/shapes/spacing) and the components in ui/components; never hard-code colors or text styles in screens. Add a component there before copying UI between screens."
- "Keep changes small and focused; update docs/plan.html when a decision changes."
- Git workflow: never commit to `main`; use `feature/<name>` branches; `./gradlew lint test` must pass; merge with `--no-ff`.
- Project-specific refinements from invest-plan.md: units in micro-units and prices in 1/10,000 rupee; "XIRR is the only float and is display-only" — [docs/invest-plan.md:17](docs/invest-plan.md); "never recompute money from" units × price; "Store the broker's actual rupee amount as the posting amount" — [docs/invest-plan.md:89](docs/invest-plan.md); "a failure is shown, never hidden behind an old price" — [docs/invest-plan.md:87](docs/invest-plan.md)

### Inferences
- A deterministic "advice" layer of pure functions returning recommendation data classes, called from `buildInvest`, follows existing patterns and needs no schema change for v1. Candidate features are new money to the underweight class, harvesting within remaining LTCG room, an FD maturing soon, an ELSS lock ending, idle cash above the emergency target, missed SIPs, and a concentration breach. Most have inputs already computed: drift, concentrations, harvestRoom, ladder, sip states and the lockUntil fields.
- Persisted features (dismiss and mute state, watchlists, goal records, a recommendation history for accountability) need Room tables, a migration and a `MigrationTest`. The DB is at version 12, and the exported schemas in `app/schemas/` stop at 11.json.
- Floats are allowed only for display ratios such as XIRR and allocation fractions. Any suggested rupee amount (for example "invest ₹X in debt") must be computed and stored as `Paise`, using BigInteger or BigDecimal rounding like `proRata` and `marketValue`.
- New thresholds (drift band, concentration limit, glide-path percentages, stress drops) are better placed in a versioned data asset or in user prefs than in constants. Tax-dependent ones are required to be in versioned data by AGENTS.md.
- Real "automation" (placing orders) would conflict with "no backend" and "estimates only, not ... advice". It would need a broker API, credentials, network permissions beyond price files, and an explicit new decision in plan.html. Recording trades the user confirms (as SIP proposals do now) fits the current design.

### Gaps
- Without running the app it is unknown how `InvestState` performs at scale. `buildInvest` recomputes everything on every snapshot, and `taxIfSoldToday` is called per holding, including again inside `harvestRoom`. This could matter if an engine adds heavier computation.
- No documentation states whether a recommender may use the network for anything beyond the two public price files.
