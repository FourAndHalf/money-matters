# Investment recommendation and automation engines: products, patterns and guardrails (India-focused, as of Oct 2026)

Research notes for a single-user, offline-first personal engine that scans a portfolio and proposes actions.

Method and coverage caveats:
- Searches ran on 2026-10-29. Most findings come from search-result summaries, not full pages. Direct fetches of www.fca.org.uk and www.wealthfront.com failed because those hosts are outside the session's allowed network domains. The Firecrawl fallback also failed because the account ran out of credits.
- So the FCA and Wealthfront figures below come from secondary summaries of those pages, not from the primary documents. Verify them before quoting them as hard numbers.
- Many Indian-product claims come from blog and comparison sites (foliyo.ai, equitiesindia.com, hisabhkaro.com), not from official help docs. Each one is flagged.

---

## 1. Indian products: what they recommend, what triggers it, what is automated, and the criticism

### Takeaway
Indian retail platforms mostly **flag** opportunities: rebalance updates, unrealised-gains reports for tax harvesting, and price-triggered orders. Execution is left to the user, apart from three things: SIPs and auto-save mandates (UPI AutoPay or NACH), broker-side conditional orders (Zerodha GTT), and the user accepting a smallcase rebalance with one tap. Full robo-style auto-rebalancing and tax-loss harvesting, of the kind Wealthfront and Betterment run, is broadly not available in India.

### Cited Findings
**smallcase (manager-driven rebalancing, user-approved execution)**
- When a smallcase manager rebalances, subscribers get a notification. Updates are not applied automatically. The user reviews and accepts them, which places buy and sell orders through their broker. Every sell in a rebalance is a capital-gains event. — [equitiesindia.com smallcase review (2026)](https://equitiesindia.com/tools-directory/smallcase-review); [Quora thread on smallcase rebalancing](https://www.quora.com/How-does-rebalancing-on-smallcase-Zerodha-works-Is-it-done-automatically-or-the-user-needs-to-make-the-changes-manually)
- The same review quotes an outdated LTCG figure (10% above Rs 1 lakh). Third-party product reviews often lag tax changes. — [equitiesindia.com](https://equitiesindia.com/tools-directory/smallcase-review)

**Zerodha GTT (Good Till Triggered): the main conditional-order automation primitive in India**
- A GTT stays valid for one year from placement. The trigger deactivates once the order reaches the exchange, whether or not it fills. — [Zerodha support: GTT validity](https://support.zerodha.com/category/trading-and-markets/kite-features/gtt/articles/what-is-the-validity-of-a-gtt-order)
- An untriggered GTT is cancelled after a year and must be placed again by hand. Each trigger fires only once. Margin is checked only when the trigger fires. Corporate actions (dividends above 5% of market value, bonuses, splits) cancel affected GTTs before the ex-date. — [Zerodha Z-Connect: Introducing GTT](https://zerodha.com/z-connect/tradezerodha/kite/introducing-gtt-good-till-triggered-orders)
- An account can hold at most 500 active GTTs. An email goes out whenever a GTT triggers and an order is placed. A sell GTT can be rejected if holdings aren't authorised (CDSL TPIN/DDPI) or are insufficient. — [Zerodha support: why was my sell GTT rejected](https://support.zerodha.com/category/trading-and-markets/charts-and-orders/gtt/articles/why-was-my-sell-gtt-order-rejected)

**Kuvera, INDmoney, Groww, Coin (mutual fund platforms)**
- Kuvera is described as having the most usable unrealised-gains report among free platforms, plus LTCG harvesting suggestions and rebalancing alerts. Groww and Zerodha Coin are listed as having neither. — [foliyo.ai platform comparison](https://foliyo.ai/guides/mf-platforms/zerodha-coin-vs-groww-vs-kuvera/) (blog, not official)
- Kuvera and INDmoney flag harvesting opportunities but cannot execute them. The user places the sell and repurchase orders. One source says Indian robo-advisors cannot fully automate rebalancing or harvesting, but cites no specific regulation. — [hisabhkaro.com robo vs human advisor India 2026](https://hisabhkaro.com/learn/ai-investing-robo-advisors-india/); [foliyo.ai](https://foliyo.ai/guides/mf-platforms/)
- INDmoney is praised for aggregation (MF folios, bank FDs, EPF). Users report FIFO inconsistencies in its capital-gains reports, so the review advises cross-checking against AMC or RTA statements before filing taxes. Its advisory layer is algorithmic or relationship-manager based, with no access to a SEBI-registered fee-only adviser. The same review asks whether it is a "free wealth app or lead-gen funnel". — [foliyo.ai INDmoney review 2026](https://foliyo.ai/guides/mf-platforms/indmoney-review/)
- Forum users call INDmoney cluttered, say free-tier support is slow, and call Groww's charts very basic. — [DesiDime discussion](https://www.desidime.com/discussions/paytm-money-vs-groww-vs-indmoney-which-is-better-for-mutual-fund-investments)
- ET Money: App Store reviews reportedly say the Genius premium paywall has grown to cover features that used to be free, such as detailed fund comparisons. Groww: redemptions sometimes sit in "processing" past the stated T+3. — [Tribune India: 6 MF platforms worth your money in 2026](https://www.tribuneindia.com/news/business/6-mutual-fund-investment-platforms-in-india-that-are-actually-worth-your-money-in-2026)

**Jar (automated micro-savings)**
- Jar rounds UPI spends up to the nearest Rs 10 and invests the difference in digital gold. — [ThePrint (sponsored ValueAd)](https://theprint.in/theprint-valuead-initiative/how-jar-app-makes-investment-in-digital-gold-easy/1112819)
- The founder calls daily savings via UPI AutoPay the "hero feature" and what most users use the app for. The company turned profitable before ESOP costs. — [TechCrunch, Sep 2025](https://techcrunch.com/2025/09/18/indian-fintech-jar-turns-profitable-by-helping-millions-save-in-gold/)
- PhonePe partnered with Jar to roll out daily savings in digital gold. — [Inc42](https://inc42.com/buzz/phonepe-partners-jar-to-roll-out-daily-savings-in-digital-gold/amp)
- I found no substantive critical reporting on the mechanics. One Medium post frames Jar as a possible "cautionary tale" without detail. — [Medium review 2026](https://medium.com/@mauryavandana1106/jar-app-review-2026-indias-favourite-digital-gullak-or-a-future-fintech-cautionary-tale-a288fb5b7259)

**Indian tax context that shapes "harvest" opportunities**
- From 23 July 2024, the listed-equity LTCG rate rose from 10% to 12.5%, and the annual exemption rose from Rs 1 lakh to Rs 1.25 lakh. The exemption applies to aggregate LTCG, not per stock. Gains before 1 Feb 2018 are grandfathered under Section 112A. — [Strike: LTCG tax on shares](https://www.strike.money/stock-market/ltcg-tax); [Quicko: Section 112A](https://learn.quicko.com/ltcg-tax-equity-shares)
- The common Indian harvesting move is the opposite of US tax-loss harvesting. You book gains up to the Rs 1.25 lakh exemption each year, then rebuy to reset the cost basis upward. Gains inside the exemption still have to be reported, and transaction costs apply. — [Strike](https://www.strike.money/stock-market/ltcg-tax)
- One broker blog says a HUF has its own Rs 1.25 lakh exemption. This is unverified. — [Ventura](https://www.venturasecurities.com/?p=54964)

### Inferences
- For a personal engine in India, the high-value automatable actions are the ones the ecosystem already treats as safe to run without a click: SIP mandates, liquid or overnight fund sweeps, and broker-native GTT orders. Rebalances and harvests are suggest-then-approve everywhere in the Indian market. That is a reasonable default to copy.
- LTCG gain harvesting (use the Rs 1.25 lakh exemption each FY) is the India-specific counterpart of US tax-loss harvesting. It is time-boxed to the financial-year end, and its value is easy to compute (up to 12.5% x Rs 1.25 lakh, or about Rs 15,625 in tax deferred or avoided per year, minus costs). That makes it a strong candidate for a ranked opportunity.
- Platform reports (INDmoney FIFO) have known errors. An engine that keeps its own lot-level ledger (as Money Matters does) should treat broker or platform P&L as data to reconcile, not as truth.
- GTT has quirks: one-year expiry, one-shot triggers, cancellation on corporate actions, and margin checked only at trigger time. An engine that relies on GTT needs a re-arm and expiry monitor, plus reconciliation after corporate actions.

### Gaps
- **Zerodha Nudge** (pre-trade risk warnings), **Coin**, **Streak** (no-code strategy and scanner product), **Kite risk alerts**: none of my searches returned primary material. Do not describe their behaviour from these notes.
- **Tickertape and Screener.in** alerts and screens, **Value Research** (fund ratings and portfolio manager), **Stable Money** (FD marketplace, maturity and reinvestment flows), **Fi** auto-save rules, **Scripbox and Paytm Money** robo features, **Dhan**: I found no reliable sources on what triggers their recommendations, how often, or what they automate. The Stable Money results were company-profile data only ([CB Insights](https://www.cbinsights.com/company/stable-money)).
- No source found for SEBI rules that explicitly prohibit discretionary auto-rebalancing for retail. Such services would need PMS or RIA registration, but that is my understanding, not a cited fact.
- Recommendation frequency (daily, weekly, event-driven) was not documented for any Indian product.

---

## 2. Global analogues: automation features and the evidence on value added

### Takeaway
US robo-advisors fully automate threshold rebalancing and continuous tax-loss harvesting. Their headline "value added" figures are self-reported and contested: critics say harvesting mostly defers tax rather than eliminating it. Betterment's SEC settlement shows that silent changes to scan frequency and coding bugs can quietly destroy the promised benefit at scale.

### Cited Findings
**Rebalancing rules**
- Betterment's default drift threshold is generally 3% for stock and bond ETF portfolios, 5% for portfolios with mutual funds, and 7% for crypto ETF portfolios. Advisors can set custom thresholds. Another Betterment page lists 3% for both ETF and MF portfolios, so the two versions conflict. — [Betterment: portfolio rebalancing methods](https://www.betterment.com/help/portfolio-rebalancing-methods); [Betterment: why rebalancing is necessary](https://betterment.com/help/portfolio-rebalancing)
- Betterment first uses cash flows (deposits, withdrawals, dividends) to buy underweight assets and sell overweight ones, before forcing any sales. It says it may change default thresholds without notice. — [Betterment rebalancing and auto-adjust disclosure](https://www.betterment.com/legal/auto-adjust-disclosure); [Betterment help](https://betterment.com/help/portfolio-rebalancing)
- Vanguard Personal Advisor Services (human-advised, not Digital Advisor) reportedly reviews quarterly and rebalances if allocation drifts 5 percentage points or more. This comes from a third-party blog. — [MyMoneyBlog](https://www.mymoneyblog.com/vanguard-personal-advisor-services-portfolio-rebalancing-rules.html)
- Vanguard's target-date research uses a 200 bp trigger with a 175 bp destination: rebalance only partway back to target. — [Vanguard: The rebalancing edge (PDF)](https://corporate.vanguard.com/content/dam/corp/research/pdf/the_rebalancing_edge_optimizing_target_date_fund_rebalancing_through_threshold_based_strategies.pdf); [Vanguard research summary](https://corporate.vanguard.com/content/corporatesite/us/en/corp/articles/balancing-act-enhancing-target-date-fund-efficiency.html)

**Tax-loss harvesting: claims and critiques**
- Wealthfront's 2025 results post reports an average annual harvesting yield of 1.59% over the last year, 3.03% over five years and 3.87% over a decade for Classic portfolios. This is harvested losses as a share of the portfolio, not net after-tax benefit, and it is self-published. — [Wealthfront: How TLH performed in 2025](https://www.wealthfront.com/blog/tlh-results-2025/) (via search summary; fetch blocked)
- Wealthfront estimated a performance gain of 0.73% to 2.6% a year (2012 to 2017). Betterment estimated 0.77%. — [CNBC 2018](https://www.cnbc.com/2018/04/16/robo-advisers-may-be-overpromising-investors-on-tax-loss-harvesting.html)
- Edesess argues TLH mostly defers tax: today's saving is repaid later unless gains are never realised. He also says Wealthfront's model assumes the top tax rate and full usability of losses. Kitces: the metric credits harvested losses without debiting the gains that must eventually be realised. — [Advisor Perspectives: The Tax Harvesting Mirage (2014)](https://www.advisorperspectives.com/articles/2014/08/12/the-tax-harvesting-mirage); [The Tax Harvesting Water Hole (2014)](https://www.advisorperspectives.com/articles/2014/09/23/the-tax-harvesting-water-hole)
- Betterment rebutted by citing peer-reviewed estimates of 0.37% and 0.50%. Its TLH+ white paper was criticised for not stating its baseline. — [CNBC 2018](https://www.cnbc.com/2018/04/16/robo-advisers-may-be-overpromising-investors-on-tax-loss-harvesting.html)
- Wealthfront's own rebuttal post. — [Wealthfront: The actual value of TLH](https://www.wealthfront.com/blog/real-value-tax-loss-harvesting/); methodology in [Wealthfront TLH whitepaper](https://research.wealthfront.com/whitepapers/tax-loss-harvesting/)
- Axios coverage quantifying TLH. — [Axios 2023](https://www.axios.com/2023/04/15/tax-loss-harvesting-roboadvisor-wealthfront)

**Betterment SEC case (April 2023): an automation failure that went unnoticed**
- Betterment paid a $9M civil penalty without admitting or denying the findings. The SEC cited three causes: a change in TLH scan frequency that was not disclosed, a programming constraint, and two coding errors that stopped losses being harvested for some clients. More than 25,000 accounts were affected, with about $4M in lost potential tax benefits. Betterment said the median payout would be under $100 and that the issues were fixed by 2019. — [CNBC 2023](https://www.cnbc.com/2023/04/19/robo-advisor-betterment-settles-tax-charges-with-sec-for-9-million.html); [InvestmentNews](https://www.investmentnews.com/betterment-pays-9-million-to-settle-sec-charges-over-tax-loss-harvesting-236576); [WealthManagement.com](https://www.wealthmanagement.com/regulation-compliance/sec-fines-betterment-9m-not-revealing-tax-loss-harvesting-errors)

**Composer (rules and AI strategy automation)**
- Composer is a no-code platform where retail investors build, back-test and automatically execute strategies on a schedule they control. It covers stocks, ETFs, crypto and options, with 3,000+ community strategies. In October 2025 it launched "Trade with AI", which turns plain-English prompts into executable, back-tested strategies. — [BusinessWire, 21 Oct 2025](https://www.businesswire.com/news/home/20251021050436/en/Composer-Supercharges-Investing-Platform-with-New-Trade-With-AI-Tool)

**Academic framing**
- A 2025 arXiv paper proposes principles and a roadmap for AI-driven planning that goes "beyond automation" in robo-advisors. It is a useful framework reference; contents not fetched. — [arXiv 2509.09922](https://arxiv.org/pdf/2509.09922)

### Inferences
- Copy the cash-flow-first rebalancing (direct new SIP and deposit money to underweight assets before selling) and the partial rebalance to a band edge (Vanguard's 200 to 175 bp). Both cut turnover and taxable events, which matters more in India because every sell is a capital-gains event and STCG carries a higher rate.
- Self-reported "harvesting yield" is not after-tax value. A personal engine should score harvests on realised after-tax rupees, net of costs and exit loads, and track any deferred liability.
- Lesson from Betterment: the worst automation failure is the silent no-op. The engine should log every scan run, alert when a scheduled scan doesn't run or produces nothing for an unusually long time, and test that harvest detection still fires on known fixtures.
- Composer's "AI prompt to executable strategy" pattern carries the highest overfitting and runaway risk for a single user. Keep LLM output on the proposal side and never let it generate and execute orders directly.

### Gaps
- No primary data fetched for **Wealthfront autopilot and cash sweep**, **Vanguard Digital Advisor** thresholds and fees, **Public and Robinhood alerts**, **Portfolio Visualizer** or **Simply Wall St**.
- Backend Benchmarking's 2021 TLH comparison was mentioned in a secondary source and not retrieved ([CB Insights stub](https://www.cbinsights.com/company/backend-benchmarking)).
- No recent (2024 to 2026) independent peer-reviewed estimate of robo TLH value was found.

---

## 3. Engine design patterns: rules vs scored feed vs agentic loop, opportunity taxonomy, ranking, explanation, decision journal

### Takeaway
I found no published spec from any product that describes its internal recommendation engine. The patterns below combine the observed product behaviours (sections 1 and 2) with the guardrail evidence (section 4). Treat them as design inferences, not cited industry practice.

### Cited Findings
- Observed trigger types in real products:
  - Drift-threshold rebalancing (Betterment, 3%/5%/7%) — [Betterment](https://www.betterment.com/help/portfolio-rebalancing-methods)
  - Manager-event rebalancing pushed for approval (smallcase) — [equitiesindia.com](https://equitiesindia.com/tools-directory/smallcase-review)
  - Price-trigger conditional orders (GTT) — [Zerodha](https://zerodha.com/z-connect/tradezerodha/kite/introducing-gtt-good-till-triggered-orders)
  - Unrealised-gain and loss reports for harvesting (Kuvera) — [foliyo.ai](https://foliyo.ai/guides/mf-platforms/zerodha-coin-vs-groww-vs-kuvera/)
  - Scheduled rules-based strategies (Composer) — [BusinessWire](https://www.businesswire.com/news/home/20251021050436/en/Composer-Supercharges-Investing-Platform-with-New-Trade-With-AI-Tool)
  - Recurring micro-debits (Jar) — [TechCrunch](https://techcrunch.com/2025/09/18/indian-fintech-jar-turns-profitable-by-helping-millions-save-in-gold/)
- Scan frequency is itself a disclosed product parameter. Changing it silently was part of the SEC's case against Betterment. — [CNBC 2023](https://www.cnbc.com/2023/04/19/robo-advisor-betterment-settles-tax-charges-with-sec-for-9-million.html)

### Inferences
- **Recommended architecture for a single user:** a deterministic **rules engine** produces candidate opportunities, a **scoring layer** ranks them on a common rupee scale, and an optional **LLM research loop** adds explanation and context only (results summaries, news) and never changes amounts or creates orders. This keeps money logic testable (per AGENTS.md: integer paise, tax data versioned per year, never in code).
- **Opportunity taxonomy:** each opportunity type maps to a trigger and to an approval tier (see section 4).

| Opportunity | Trigger | Approval tier |
|---|---|---|
| Rebalance | Drift beyond band; prefer cash-flow rebalancing | Approve |
| Deploy idle cash | Savings balance above runway buffer for N days | Approve, or auto into liquid fund |
| LTCG gain harvest | FY-end window; unused part of Rs 1.25 lakh exemption | Approve |
| Tax-loss set-off | Realised STCG this FY with unrealised losses available | Approve |
| FD maturity / reinvest | Maturity within T days; compare rates net of TDS | Approve |
| SIP step-up | Annual date or salary-change event | Approve once, then automatic |
| Fund overlap / duplicate exposure | Overlap above X% between held funds | Inform |
| Cost reduction | Regular to direct plan switch; expense-ratio gap; exit-load-free date reached | Approve |
| Watchlist price trigger | GTT-like threshold crossed | Inform, or broker-side GTT |
| Results / corporate events | Earnings, splits, dividends affecting GTTs | Inform only |

- **Ranking:**
  - Score = expected after-tax benefit in rupees minus costs (brokerage, STT, stamp duty, exit load, tax triggered now), multiplied by confidence, and adjusted for urgency (deadline decay, e.g. 31 March).
  - Suppress anything whose net benefit falls below a materiality floor (for example Rs 500, or a percentage of the portfolio).
  - Every card should show: the computation, the inputs with their as-of dates, the rule or IPS clause that fired, and what happens if the user ignores it.
- **Decision journal:**
  - Persist each proposal with its inputs snapshot and score.
  - Record the user decision (accepted, rejected or snoozed, with a reason).
  - Track the outcome at fixed horizons (30, 90 and 365 days) against the counterfactual of doing nothing.
  - Feed hit rate and realised value back into confidence weights per opportunity type.
  - This gives the post-hoc scoring the brief asks for. It also catches a "silent no-op" scanner like Betterment's.

### Gaps
- No product publishes its ranking formula, confidence model or decision journal. No primary sources were found on opportunity-feed design in fintech, or on LLM agent loops in live portfolio management.

---

## 4. Guardrails: IPS as code, limits, caps, cooling-off, kill switch, approval tiers, alert budgets, anti-gamification

### Takeaway
The strongest evidence is behavioural. Push notifications and gamified features measurably raise trading frequency and risk-taking, most among young and less financially literate users. In India, about 91% of individual F&O traders lose money, and the loss rate rises with tenure. An engine that "works relentlessly" should therefore be relentless in **scanning**, frugal in **interrupting**, and conservative in **acting**.

### Cited Findings
**Notifications and gamification raise trading**
- FCA experiment with more than 9,000 consumers: push notifications raised trading frequency by about 11%, and points with a prize draw by about 12%. Effects were stronger among 18 to 34 year olds and people with lower financial literacy. Leaderboards did not raise trade counts the same way. — [FCA research note: digital engagement practices in trading apps (PDF)](https://www.fca.org.uk/publication/research-notes/research-note-digital-engagement-practices-trading-apps-experiment.pdf) (figures via search summary; direct fetch blocked, so verify)
- Moss (2022): Robinhood push notifications raised retail trading intensity by about 25% in the 15 minutes after an alert. A University of Iowa dissertation found a similar about 25% lift, with notified users often trading in a contrarian direction. — [U. Iowa dissertation on brokerages' digital engagement practices](https://iro.uiowa.edu/view/pdfCoverPage?instCode=01IOWA_INST&filePid=13873804220002771&download=true); [ResearchGate figure: trading activity around push messages](https://www.researchgate.net/figure/Trading-activity-around-push-messages_fig1_342283509)
- Arnold et al. (2022): broker push notifications about large price moves increased use of leverage. Barber et al. (2022): Robinhood users show more attention-induced trading. — cited in [Trading Gamification and Investor Behavior (ResearchGate)](https://www.researchgate.net/publication/381495416_Trading_Gamification_and_Investor_Behavior); [Does gamified trading stimulate risk taking?](https://www.researchgate.net/publication/356607751_Does_gamified_trading_stimulate_risk_taking)
- Massachusetts regulators sued Robinhood, alleging that gamified features including push notifications encouraged frequent and unsuitable trading. This is an allegation, not a finding. — [Cato: The Trading Game](https://www.cato.org/commentary/trading-game)
- A February 2026 preprint says notifications drive much of gamification's effect. It is not peer-reviewed. — [clawxiv 2602.00030](https://www.clawxiv.org/api/pdf/clawxiv.2602.00030)

**India: regulatory and outcome context**
- SEBI FY25 study:
  - 91% of individual F&O traders lost money (91.1% in FY24).
  - Net losses after costs were Rs 1,05,603 crore, up from Rs 74,812 crore.
  - The average loss was about Rs 1.1 lakh per person.
  - Unique traders fell from 61.4 lakh in Q1 to 42.7 lakh in Q4.
  
  — [Business Standard](https://www.business-standard.com/markets/news/net-losses-of-traders-in-fo-widens-in-fy25-sebi-study-125070701221_1.html); [Moneylife](https://moneylife.in/article/91-percentage-of-retail-traders-lost-money-in-derivatives-losses-in-fo-surged-41-percentage-to-rs105-lakh-crore-in-fy2425-sebi-study/77613.html); [Value Research](https://www.valueresearchonline.com/stories/225398/average-trader-lost-rs-1-1-lakh-fo-fy25/)
- A later SEBI study (the fourth in the series, covering FY25 and FY26) found the share of loss-makers rises from about 91% among first-year traders to over 95% among traders active four or more consecutive years. — [Outlook Money](https://www.outlookmoney.com/invest/sebi-futures-options-study-retail-traders-trading-losses-experience) (secondary; primary SEBI report not fetched)
- An academic paper on Indian brokerage apps says SEBI consultation papers from 2022 and 2023 acknowledged game-like features that encourage excessive trading, but current rules mainly require disclosure rather than addressing behavioural design. — [ResearchGate: Impact of gamification in brokerage apps on young Indian investors](https://www.researchgate.net/publication/401473592_THE_IMPACT_OF_GAMIFICATION_IN_BROKERAGE_APPS_ON_INVESTMENT_DECISIONS_OF_YOUNG_INVESTORS_IN_INDIA)
- Finfluencer timeline:
  - SEBI consultation paper, 25 Aug 2023 — [Khaitan & Co](https://www.khaitanco.com/thought-leaderships/Finfluencers_In_The_Crosshairs_SEBIs_Regulatory_Clampdown)
  - "Specified Digital Platforms" consultation, 22 Oct 2024 — [Lexology](https://www.lexology.com/library/detail.aspx?g=a66346d5-927d-4473-b1cd-9d7617059bc9)
  - Directive in Oct 2024 that regulated entities sever ties with unregistered finfluencers — [IndiaCorpLaw](https://indiacorplaw.in/2025/04/08/margin-call-on-misinformation-sebis-crackdown-on-finfluencers/)
  - Parliamentary panel in Aug 2025 recommending a verified-tick system — [MediaNama](https://www.medianama.com/2025/08/223-parliament-panel-sebi-finfluencer-regulation-india/)
  - Zerodha's analysis of the 2023 paper — [Zerodha Z-Connect](https://zerodha.com/z-connect/general/decoding-the-sebi-consultation-paper-on-regulating-financial-influencers)
- A commercial compliance blog lists trade-volume leaderboards and F&O activity rewards as risky features under SEBI scrutiny. — [openwebsolutions.in](https://openwebsolutions.in/blog/gamification-in-trading-apps-sebi-compliance-guide/) (not authoritative)

**Rebalancing bands as turnover limiters**
- Threshold bands (3% Betterment; 200 bp trigger with a 175 bp destination at Vanguard) are the industry's built-in cap on turnover. — [Betterment](https://www.betterment.com/help/portfolio-rebalancing-methods); [Vanguard PDF](https://corporate.vanguard.com/content/dam/corp/research/pdf/the_rebalancing_edge_optimizing_target_date_fund_rebalancing_through_threshold_based_strategies.pdf)

### Inferences
**IPS as code: a versioned, user-signed policy object the engine must satisfy before proposing anything:**
- Target allocation with bands.
- Per-instrument cap (for example 5 to 10% in any single stock) and per-sector cap.
- Banned instruments: no F&O, no margin, no intraday, given SEBI's loss data.
- Minimum emergency or runway buffer, linked to Money Matters' leeway and runway projections.
- Maximum annual turnover (for example 20% of portfolio).
- Maximum trades per week or month.
- Minimum holding period, to avoid STCG churn.
- A materiality floor.
- Changes to the IPS itself go through a cooling-off period (for example 72 hours) before taking effect, so the user cannot loosen limits in the moment.

**Approval tiers:**
- **Tier 0, auto-execute:** pre-approved, low-risk, reversible actions inside the IPS. Examples: SIP instalments on existing mandates, sweeping cash above the runway buffer into a named liquid or overnight fund, re-arming an expiring GTT with identical parameters.
- **Tier 1, one-tap approve:** rebalances inside bands, LTCG gain harvest, FD reinvest, regular-to-direct switch.
- **Tier 2, approve after a cooling-off or with a written rationale:** anything that adds a new single stock, exceeds a cap or sells at a large tax cost.
- **Never automatable:** leverage, derivatives, IPS changes, new payees or mandates.

**Other safeguards:**
- Kill switch: one global toggle that pauses all Tier 0 actions and hides proposals. Auto-trip on anomalies such as a data feed gap, a price move beyond X sigma, an order rejection streak, or ledger imbalance.
- Dry-run or paper mode is the default for at least the first N weeks. Every proposal records a "would have" outcome in the decision journal.

**Alert budget, to counter the evidence above:**
- Batch non-urgent opportunities into a weekly digest. Interrupt only for deadline-bound items (FD maturity, FY-end harvest window, GTT expiry).
- Never push notifications about price movement alone.
- No streaks, confetti, leaderboards or "you missed X% gains" framing.
- Show "do nothing" as a first-class, often-recommended option.

### Gaps
- I could not retrieve the primary SEBI 2022 and 2023 gamification consultation text, and could not confirm whether SEBI issued a gamification-specific paper in 2025 or 2026.
- No empirical study was found on optimal alert budgets or digest frequency for personal finance apps.

---

## 5. Security for automation that holds broker credentials

### Takeaway
SEBI's retail algo framework (circular of 4 Feb 2025, NSE implementation standards of 5 May 2025, reportedly mandatory from 1 Apr 2026) requires:
- OAuth with 2FA for API access.
- Vendor- and client-specific API keys.
- A static IP whitelisted by the broker.
- Daily forced session expiry.

Together these make unattended, phone-resident, fully autonomous order placement difficult by design. A personal Android engine should assume a human re-authenticates daily.

### Cited Findings
- Requirements:
  - No open APIs; access only via a unique vendor- and client-specific API key and a static IP whitelisted by the broker.
  - OAuth authentication and 2FA are mandatory.
  - Algos are registered with the exchange only above an orders-per-second threshold (NSE's initial figure: 10 OPS per exchange per client). Orders below it are tagged with a generic algo ID.
  - Retail users may run self-built algos for themselves and their family but cannot offer them to others.
  
  — [Mondaq: SEBI circular on safeguarding retail investors in algo trading](https://www.mondaq.com/india/commoditiesderivativesstock-exchanges/1581380/sebi-circular-safeguarding-retail-investors-in-algorithmic-trading); [MediaNama, Feb 2025](https://www.medianama.com/2025/02/223-sebi-proposes-framework-retail-investors-algo-trading/); [Finseclaw](https://www.finseclaw.com/article/finsec-tracker-on-sebi-issues-guidelines-on-retail-participation-in-algorithmic-trading)
- API sessions are closed automatically at the end of each trading day; continuous sessions are no longer allowed. Brokers began registering retail algo products with exchanges in Oct 2025, and the framework became mandatory for all brokers from 1 Apr 2026. — [Tradejini blog](https://www.tradejini.com/blogs/what-sebis-new-algo-trading-rules-mean-for-you); [AlgoBulls](https://algobulls.com/blog/industry-insights-and-updates/sebi-new-algotrading-regulations-for-retail-investors-2026); [Liquide](https://blog.liquide.life/sebi-algo-trading-regulations-2026/) (broker and vendor blogs; the 1 Apr 2026 date was not confirmed against a primary SEBI or NSE notice; blogs disagree on some details such as family sharing)
- Kite Connect specifics:
  - The `request_token` is valid for about 5 minutes and works once.
  - The `access_token` lasts one trading day and should be stored and reused, not regenerated on every run.
  - An "Invalid or expired connect session" error means restarting the login flow.
  - Clearing active app sessions from Kite web kills the API session.
  - The exact expiry time (a fixed 6 AM IST versus 24 hours) is asked about on the forum but not answered in the results.
  
  — [kite.trade forum: token answer](https://kite.trade/forum/discussion/comment/52919/); [expiry time question](https://kite.trade/forum/discussion/comment/51353); [login automation thread](https://kite.trade/forum/discussion/6690/login-automation)
- Sell orders from demat holdings need CDSL authorisation (TPIN or DDPI). Otherwise sell GTTs are rejected. — [Zerodha support](https://support.zerodha.com/category/trading-and-markets/charts-and-orders/gtt/articles/why-was-my-sell-gtt-order-rejected)

### Inferences
- A phone has no static IP, so placing orders directly from an Android app through a personal API key likely conflicts with the static-IP rule. Realistic options are:
  1. Propose in the app and execute in the broker's own app (deep link or manual entry).
  2. Use broker-native, broker-side automation (GTT, SIP, mandates), which needs no API session.
  3. Run a small self-hosted relay on a whitelisted static IP, which adds attack surface.
  
  For an offline-first personal app, options 1 and 2 cover almost all low-turnover investing.
- Do not automate TOTP generation. Storing the TOTP seed next to the password in the same place collapses 2FA into a single factor and defeats the intent of SEBI's 2FA mandate. Keep the daily login human.
- If tokens are held at all:
  - Store the access token encrypted with a key held in Android Keystore (hardware-backed, non-exportable where available), gated on user authentication (BiometricPrompt) for order-placing scopes.
  - Never sync tokens to backups.
  - Wipe them at end of day, to match broker expiry.
  - Prefer read-only scopes (holdings and positions) for scanning.
- Mutual fund SIPs and FD auto-renewals run on bank, AMC or RTA mandates (NACH, UPI AutoPay). This is safer than broker API automation because no credential sits in the app.

### Gaps
- No primary source fetched for Android Keystore or BiometricPrompt guidance, or for Zerodha's current official Kite Connect docs on token expiry and OAuth migration after the SEBI framework.
- No source on whether SEBI or brokers explicitly prohibit automated TOTP generation by end users. The inference above is a security judgement, not a cited rule.
- Unconfirmed whether read-only (non-order) API access is also bound by the static-IP requirement.

---

## 6. Lessons from failures: runaway automation, robo errors, retail losses

### Takeaway
The canonical failures share a few causes:
- Dormant or partially deployed code (Knight).
- Silent degradation of an automated benefit (Betterment).
- Engagement-driven trading with leverage (Robinhood allegations; SEBI F&O losses).

Each maps to a concrete guardrail: deployment hygiene and kill switches, liveness monitoring with scored outcomes, and banned instruments plus alert budgets.

### Cited Findings
- **Knight Capital (1 Aug 2012):**
  - A deployment error reactivated a dormant legacy "Power Peg" routine. The new release reached only some servers, so the old logic ran on the one that was missed.
  - It lost about $440 to 460M in about 45 minutes. Knight's SEC filing puts the figure at about $461.1M.
  - Warnings were not escalated or acted on.
  - The firm was later acquired.
  
  — [Knight Capital Form 8-K exhibit (SEC)](https://www.sec.gov/Archives/edgar/data/0001060749/000119312512513607/d457296dex21.htm); [PRMIA case study (PDF)](https://prmia.org/common/Uploaded%20files/eAI/PRMIA%20Case%20study%20-%20Knight%20Trading.pdf); [CIO.com: testing lessons](https://www.cio.com/article/286790/software-testing-lessons-learned-from-knight-capital-fiasco.html)
- **Betterment TLH (SEC, 2023):** an undisclosed change in scan frequency plus coding errors affected more than 25,000 accounts and cost about $4M in tax benefits. The penalty was $9M. — [CNBC](https://www.cnbc.com/2023/04/19/robo-advisor-betterment-settles-tax-charges-with-sec-for-9-million.html); [Financial Planning](https://www.financial-planning.com/news/robo-advisor-betterment-settles-with-sec-for-9-million-for-overpromising-tax-strategies)
- **Indian retail F&O:** 91% of individual traders lost money in FY25, with Rs 1.05 lakh crore in net losses. — [Moneylife](https://moneylife.in/article/91-percentage-of-retail-traders-lost-money-in-derivatives-losses-in-fo-surged-41-percentage-to-rs105-lakh-crore-in-fy2425-sebi-study/77613.html)
- **Robinhood:** Massachusetts alleged that gamification including push notifications led to unsuitable trading. — [Cato](https://www.cato.org/commentary/trading-game)

### Inferences
- Knight → the engine needs:
  - A feature-flag registry with no dead code paths able to place orders.
  - Version pinning of rules and tax data per tax year.
  - Hard per-day caps on order count and value, enforced independently of the strategy code.
  - Auto-trip of the kill switch on anomalies.
  - Every executed action written as a balanced ledger entry and reconciled against the broker contract note the same day.
- Betterment → monitor the engine itself:
  - Alert on "scan didn't run" or "no proposals in N days when fixtures say there should be".
  - Keep a regression suite of known-opportunity fixtures (aligns with AGENTS.md's requirement for tests on ledger and tax logic).
- F&O and Robinhood → hard-ban derivatives and leverage in the IPS, and keep notification counts on a budget.

### Gaps
- No sourced Indian retail algo or bot incident found, such as a Streak or third-party algo-platform runaway, API-key theft from a strategy vendor, or a "tips" bot scam. Worth a targeted search in Moneycontrol, ET or Entrackr.
- No sourced Wealthfront incident found.
- Unverified lead, not confirmed in this session: the June 2020 Robinhood options-display case (Alex Kearns). I did not retrieve a source for it.

Your Firecrawl credits ran out. Add credits or upgrade to keep going: https://www.firecrawl.dev/billing/s/LlwmIJTnLx75ZXjmwozegds5l518bJkqY59pS_xtT8s
