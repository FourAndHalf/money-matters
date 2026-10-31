# India: regulation and execution rails for a personal investment recommendation and automation app (status as of late October 2026)

Research method note: Every web fetch was blocked in this session. Substack, fyers.in, angelone.in, nseindia.com, taxguru.in and similar hosts did not resolve, and Firecrawl had no credits left. So all findings below come from web-search result summaries and snippets, not from primary documents read in full. Circular numbers and dates that turn up in several independent results are marked as corroborated. Anything that came from a single blog is flagged. Primary texts (SEBI and NSE circulars, the Income-tax Act 2025 text, broker docs) still need a direct read before they are used in code or in tax data files.

## 1. SEBI retail algorithmic trading framework: what applies to a retail user who writes their own algo against a broker API

### Takeaway
SEBI's circular of 4 Feb 2025 ("Safer participation of retail investors in Algorithmic trading", SEBI/HO/MIRSD/MIRSD-PoD/P/CIR/2025/0000013) has applied to all brokers since **1 April 2026**, after two deferrals. A self-coded personal algo that stays at or under about 10 orders per second does **not** need exchange registration, but it still runs inside the new controls:
- order APIs only from a whitelisted static IP (one primary and one backup);
- a client-specific API key and OAuth;
- a daily 2FA login, with no long-lived refresh sessions;
- every order tagged with an exchange Algo ID (the broker's generic ID when below the threshold);
- market orders sent with non-zero market protection.

A low-frequency personal investing app (a few delivery orders a week) fits comfortably under these rules. The daily-login rule is the main UX constraint.

### Cited Findings
**Primary circulars (numbers corroborated across several secondary sources)**
- SEBI issued the circular on 4 Feb 2025. It bans open APIs: access must go through a unique vendor- or client-specific API key and a static IP whitelisted by the broker, with OAuth-based authentication and 2FA mandatory. — [Mondaq summary](https://mondaq.com:443/india/commoditiesderivativesstock-exchanges/1581380/sebi-circular-safeguarding-retail-investors-in-algorithmic-trading); [IndiaLaw summary](https://www.indialaw.in/blog/civil/sebi-retail-investors-algorithmic-trading/)
- NSE's implementation standards are in circular **NSE/INVG/67858 dated 5 May 2025**. It cites SEBI circular SEBI/HO/MIRSD/MIRSD-PoD/P/2025/0000013 and an earlier NSE circular **NSE/INVG/66524 of 5 Feb 2025**. Its annexure sets operational specs for algos offered through IBT, STWT, and client and vendor APIs. — [NSE circular PDF (not fetched; cited via search)](https://nsearchives.nseindia.com/content/circulars/INVG67858.pdf); [Zerodha Z-Connect overview of the NSE circular](https://zerodha.com/z-connect/general/a-comprehensive-overview-of-nses-circular-on-the-new-retail-algo-trading-framework)
- A further NSE circular on "detailed operational modalities" is dated around 23 July 2025. At that point it gave 1 Aug 2025 as the implementation date. — [Scribd copy of NSE July 2025 circular](https://www.scribd.com/document/946709195/NSE-Issued-a-Circular-Regarding-Safer-Participation-of-Retail-Investors-in-Algorithmic-Trading-Detailed-Operational-Modalities-July232025)

**Timeline**
- The original target was 1 Aug 2025. It was extended to Oct 2025 and finally set at **1 April 2026** for all brokers. — [AlgoBulls](https://algobulls.com/blog/industry-insights-and-updates/sebi-new-algotrading-regulations-for-retail-investors-2026); [OpenAlgo "State of Algo Trading in India 2026"](https://blog.openalgo.in/state-of-algo-trading-in-india-2026-rules-platforms-costs-91fcc19a0d20?gi=8e9e1b971946)
- Fyers published a notice titled "New SEBI Framework for Retail Algo Trading from April 01, 2026". — [Fyers notice](https://fyers.in/notice-board/new-sebi-framework-for-retail-algo-trading-from-april-01-2026)
- Zerodha's support article says that **from 1 April 2026, API order placement requires a static IP** under the NSE and SEBI rules. — [Zerodha support: static IP](https://support.zerodha.com/category/trading-and-markets/general-kite/kite-api/articles/static-ip)

**Order-per-second threshold and Algo IDs**
- Orders above a specified orders-per-second (OPS) threshold count as algo orders and need a unique exchange identifier. Retail users who build their own algos must register them with the exchange through their broker only if those algos exceed the threshold. — [Mondaq](https://mondaq.com:443/india/commoditiesderivativesstock-exchanges/1581380/sebi-circular-safeguarding-retail-investors-in-algorithmic-trading)
- Exchanges set the threshold at **10 OPS**. Below it, algos run under a generic Algo ID with no individual registration. Sources differ on the boundary wording: "10 or more on a single exchange" versus "above 10". — [Kotak Neo explainer](https://www.kotakneo.com/investing-guide/articles/retail-algo-trading-rules-nse-circular/); [ICICIdirect explainer](https://www.icicidirect.com/futures-and-options/articles/algorithmic-trading-new-rules-by-sebi-nse-retail-participation-with-safety-and-structure); [Kite forum thread on compliance prep](https://kite.trade/forum/discussion/15912/preparing-to-comply-with-sebis-retail-algo-rules-static-ip-ratelimits-order-types)
- "Black box" strategy providers (whose logic is not disclosed to the user) must hold a SEBI Research Analyst licence and periodically disclose performance. This is a secondary source. — [Liquide blog](https://blog.liquide.life/sebi-algo-trading-regulations-2026/)

**Static IP rules**
- A client may register one primary and one secondary (backup) static IP, mapped at the trading-account level. The IP can be changed only once per calendar week. — [Zerodha support: static IP](https://support.zerodha.com/category/trading-and-markets/general-kite/kite-api/articles/static-ip); [Kotak Neo](https://www.kotakneo.com/investing-guide/articles/retail-algo-trading-rules-nse-circular/)
- Only **order** endpoints are IP-checked. Market data, WebSocket, order book and positions calls work from any IP. — [Zerodha support: static IP](https://support.zerodha.com/category/trading-and-markets/general-kite/kite-api/articles/static-ip)
- **Family sharing of an IP (sources conflict):**
  - Several broker and vendor explainers say the NSE framework lets an IP be shared within the "family" as SEBI defines it (self, spouse, dependent children, dependent parents). Sharing needs broker pre-approval by written request, registered-email request or 2FA-validated request. — [TrueData](https://www.truedata.in/blog/new-nse-rules-on-retail-algo-trading-what-traders-platforms-and-brokers-need-to-know); [Marketcalls](https://www.marketcalls.in/market-regulations/new-nse-rules-for-retail-algo-trading-what-retail-traders-algo-platforms-and-brokers-must-now-do.html)
  - A Zerodha forum reply says a static IP should be unique and **cannot** be used within the family on Kite. — [Kite forum: Market Protection and Static IP](https://kite.trade/forum/discussion/comment/52028/)
- The same "immediate family" definition applies to a **registered** self-developed algo, which the investor, spouse, dependent children and dependent parents may use. — [Mondaq](https://mondaq.com:443/india/commoditiesderivativesstock-exchanges/1581380/sebi-circular-safeguarding-retail-investors-in-algorithmic-trading); [Upstox community interpretation thread](https://community.upstox.com/t/interpretation-of-sebi-circular-titled-safer-participation-of-retail-investors-in-algorithmic-trading/8020)

**Sessions and order types**
- API sessions must be logged out at the end of each trading day, so 2FA is needed once per trading day. Continuous refresh-token sessions are no longer supported. — [Kotak Neo](https://www.kotakneo.com/investing-guide/articles/retail-algo-trading-rules-nse-circular/); [Angel One news on NSE guidelines](https://www.angelone.in/news/market-updates/nse-issues-fresh-guidelines-on-retail-participation-algo-trading)
- Market orders, including SL-M orders, sent with market protection "0" are rejected. Exchanges mandate market protection on all API/algo orders. — [Kite forum: Market Protection and Static IP](https://kite.trade/forum/discussion/comment/52028/); [Kite forum compliance thread](https://kite.trade/forum/discussion/15912/preparing-to-comply-with-sebis-retail-algo-rules-static-ip-ratelimits-order-types)
- By 2026, most Indian brokers offer MCP (LLM-agent) servers, but they differ on whether the agent may place orders. This is a secondary source. — [QuantLab substack (title/snippet only)](https://quantlabin.substack.com/p/every-indian-broker-has-an-mcp-now)

### Inferences
- An Android app that places orders **directly from the phone** is a poor fit. Mobile carrier IPs are dynamic and CGNAT'd, so they cannot be whitelisted. Order placement would need a small relay on a static-IP host (a VPS or a static-IP egress service, owned by the user) or a home connection with a static IP. Reads (holdings, quotes) can still go direct from the device.
- A personal investing cadence (SIP-like buys, rebalancing) sits far below 10 OPS, so no strategy registration is needed. The broker's generic Algo ID covers it.
- The daily 2FA/logout rule means "fully unattended" automation is not possible. The app has to prompt the user to log in each trading day it needs to place orders. The practical pattern is "recommend, then confirm with a daily login, then execute".
- Sharing one deployment with a spouse or parents is legally contemplated under the family definition, but broker policy decides in practice. Zerodha appears stricter.

### Gaps
- Could not read NSE/INVG/67858 or the July 2025 modalities circular in full. The exact threshold wording (≥10 vs >10, and per exchange vs per client), the Algo ID format and the logout cut-off time are unverified against primary text.
- No primary confirmation of any post-April-2026 amendment, relaxation or further deferral. None appeared in the search results.
- BSE's parallel implementation-standards circular number was not found.

## 2. When does a recommendation tool trigger SEBI Investment Adviser (IA) or Research Analyst (RA) registration?

### Takeaway
No registration is needed to build a tool that recommends trades **only to yourself**: both regimes regulate advice and research provided to *clients* or *for consideration*. The risk starts when the outputs are shared.
- **Advising others for consideration** (money or non-cash benefit) brings in the IA Regulations 2013.
- **Publishing or distributing buy/sell/hold calls or target prices**, even free, can count as "research services" under the RA Regulations 2014. These were amended in Dec 2024 to add "research services" and "trading calls".
- **Selling a black-box algo** requires RA registration.
- **Showing near-live price data or performance claims publicly** as "education" is restricted (30-day data lag from May 2026).

Neither regulation appears to have an explicit "family" exemption.

### Cited Findings
- **IA Regulations (consideration test):** Advice given without any consideration (economic or non-cash benefit) is not "investment advice" under the IA Regulations. Because "consideration" includes non-cash benefits, informal arrangements still need care. — [Nishith Desai: IA regulations hotline](https://www.nishithdesai.com/default.aspx?id=5966); [Mondaq: do you need to register](https://www.mondaq.com/india/securities/223906/investment-advisers-regulations-notified-do-you-need-to-register)
- **No family exemption found:**
  - The IA Regulations define "family of client" (dependent spouse, children, parents) and "family of an individual investment adviser" (spouse, children, parents). These definitions are used for fee and implementation rules, not as a registration exemption. — [SEBI regulations PDF (Mar 2023 consolidated)](https://www.sebi.gov.in/sebi_data/attachdocs/mar-2023/1679546065355.pdf)
  - The exemptions that do exist cover, for example, advice given exclusively to clients based outside India, and persons regulated under another regime. — [Telfa law summary](https://www.telfa.law/?p=3345)
- **RA scope:** Giving buy/sell/hold recommendations, publishing target prices and selling model portfolios can all count as research services needing registration. — [AlgoTest RA explainer](https://algotest.in/blog/research-analysts-demystified-what-your-finfluencer-needs-to-do-now-sebi/)
- **Dec 2024 overhaul:** The IA and RA amendments were notified on **16 Dec 2024** and took effect on publication. They added the definitions "research services", "trading calls" and "persons associated with research services". — [SCC Online](https://scconline.com/blog/?p=337543); [AZB Partners, 3 Feb 2025](https://www.azbpartners.com/bank/overhaul-of-the-regulatory-framework-for-investment-advisers-and-research-analysts/)
- **AI disclosure and responsibility:**
  - From guidelines dated **8 Jan 2025**, IAs and RAs must tell clients how much they use AI tools in their research or advice. — [Fox Mandal](https://foxmandal.in/news/ras-and-ias-to-take-on-additional-responsibilities/)
  - The SEBI Intermediaries (Amendment) Regulations, 2025, notified 10 Feb 2025, make regulated entities responsible for the output of the AI tools they use. — [Fox Mandal: AI output responsibility](https://foxmandal.in/news/regulated-entities-responsible-for-output-of-ai-usage-sebi/)
- **Further RA amendment:** A "Second Amendment" to the RA Regulations, dated around 25 Nov 2025, changed qualification and registration norms. Sources disagree on its numbering. — [Advocate Gandhi](https://advocategandhi.com/major-changes-introduced-by-sebi-for-research-analysts-understanding-the-2025-second-amendment-regulations/)
- **Finfluencer / education rules:**
  - SEBI's circular of **29 Jan 2025** required pure "education" providers to use price data at least 3 months old. It barred them from advising on specific securities or making performance claims unless registered. — [Storyboard18](https://www.storyboard18.com/social-media/sebi-cracks-down-on-finfluencers-with-new-rules-on-stock-market-education-55026.htm); [Moneylife](https://www.moneylife.in/article/live-trading-tips-on-social-media-sebi-warns-investors-to-deal-only-with-registered-intermediaries/81385.html)
  - A SEBI circular of **8 May 2026** set a **30-day lag** for market price data shared for education, with no monetary incentives. — [TaxGuru: SEBI conditions on educational real-time data](https://taxguru.in/sebi/sebi-sets-conditions-educational-real-time-market-data.html); [Bigul](https://bigul.co/blog/market-update/sebis-30day-rule-for-market-data-what-stock-market-educators-must-know-before-july-1)
  - SEBI warned against "live trading strategies" on social media in an Aug 2026 press release. — [PSU Watch](https://psuwatch.com/amp/story/newsupdates/sebi-cautions-investors-against-live-trading-strategies-on-social-media)
- **Enforcement context:** A 2026 report found that 33% of finfluencers recommend stocks but only about 6% are SEBI-registered. — [Business Standard, Jul 2026](https://www.business-standard.com/amp/finance/news/33-finfluencers-recommend-stocks-but-only-6-are-sebi-registered-report-126072801516_1.html)

### Inferences
- Single-user, on-device use with no fee and no distribution is outside both regimes. A closed family deployment with no payment is likely low-risk, but there is no explicit safe harbour.
- These steps would plausibly bring in RA and/or IA regulation:
  - publishing the engine's picks (blog, Play Store app for others, Telegram);
  - charging for the app;
  - offering it as a "strategy" others can subscribe to.
- If the app is ever distributed, it should stay "tool-like": user-configured rules, no house picks, no performance marketing. It should also avoid showing near-live third-party price data in any public or educational context.

### Gaps
- Could not read the IA or RA Regulations' exemption clauses (Reg. 4 of IA, Reg. 3 of RA) verbatim. Whether a free tool shared with non-dependent relatives or friends is exempt remains unconfirmed.
- No SEBI FAQ or informal guidance on personal or self-use algorithmic recommenders was found.

## 3. Broker APIs for stocks (pricing, capabilities, auth/token rules)

### Takeaway
Since 2025, order-placement APIs at most large brokers are free. Market-data feeds cost about ₹499–500 a month at Zerodha, Dhan and Groww. Upstox charges a per-order API fee instead. Every broker now needs a daily login, and order calls must come from a static IP, so the on-device mobile use described in section 1 is limited for all of them.

### Cited Findings
- **Zerodha Kite Connect: plans**
  - The free "Personal" plan covers full order, GTT and alerts management, margin computation and portfolio/holdings. It has **no** historical or real-time data. — [Zerodha support: Kite API charges](https://support.zerodha.com/category/trading-and-markets/kite-web-and-mobile/kite-api/articles/what-are-the-charges-for-kite-apis)
  - The "Connect" plan costs **₹500/month** and adds WebSocket streaming and historical candles. The fee was cut from ₹2,000. — [Zerodha support](https://support.zerodha.com/category/trading-and-markets/kite-web-and-mobile/kite-api/articles/what-are-the-charges-for-kite-apis); [Kite forum: revising fees ₹2000→₹500](https://kite.trade/forum/discussion/comment/49597)
  - Order and account APIs have been free since **March 2025**. — [Kite forum: is order placement and GTT free?](https://kite.trade/forum/discussion/15603/is-order-placement-market-limit-etc-and-gtt-gtt-oco-orders-free)
- **Zerodha Kite Connect: MF endpoints** are read and manage only: `GET /mf/orders`, `/mf/orders/:id`, `/mf/sips`, `/mf/holdings`, `/mf/instruments`. The docs say MF order placement cannot be done via the API because it needs payment from the user's bank. — [Kite Connect v3 MF docs](https://kite.trade/docs/connect/v3/mutual-funds/); [Kite forum thread](https://kite.trade/forum/discussion/12587/to-get-list-of-mutual-funds-sip-approved-available-on-zerodha-api-suite)
- **Upstox**
  - Trading and data APIs are free, but **₹10 per executed API order**. A comparison site cites Upstox staff posts of 5–6 Oct 2026 saying this rate runs to 31 Dec 2026; Upstox's own pages show conflicting validity dates. Another source cites up to ₹20 per order. — [OneTradeJournal: algo trading charges](https://onetradejournal.com/trading-apis/algo-trading-charges); [Upstox community: brokerage for API trades](https://community.upstox.com/t/regarding-brokerage-charges-for-algo-trades-via-api/13232); [AlgoTest broker comparison](https://algotest.in/blog/best-brokers-for-algo-trading-in-india/)
  - The standard OAuth access token **expires daily at 3:30 AM IST**. A separate read-only "Analytics token" lasts **one year**. — [CernoQuant: Upstox access token](https://cernoquant.com/blog/upstox-api-access-token)
- **Dhan:** The trading API is free. The Data API costs **₹499/month + taxes**. One comparison describes Dhan's token as long-lived, but that predates the April 2026 daily-2FA rule. — [CernoQuant: Dhan API charges](https://cernoquant.com/blog/dhan-api-charges); [CernoQuant: broker API comparison](https://cernoquant.com/blog/broker-api-charges-comparison)
- **Angel One SmartAPI and Fyers:** Both are described as free, with Angel using a TOTP-based daily login and Fyers a daily OAuth login. This is a secondary source; no official fee page was found. — [CernoQuant comparison](https://cernoquant.com/blog/broker-api-charges-comparison); [Stratzy cost comparison](https://stratzy.in/blog/cost-comparison-algo-trading-apis-india)
- **Groww Trading API**
  - Flat **₹499/month + taxes** (cut from ₹2,000), with no usage fees. It covers market data, order placement and modification, holdings and positions, margins, and historical OHLC for equity, F&O and MTF on NSE and BSE. — [Marketcalls: Groww enters algo arena](https://www.marketcalls.in/fintech/groww-enters-the-algo-arena-with-powerful-trading-apis.html); [Chittorgarh review](https://www.chittorgarh.com/broker/groww/api-for-algo-trading-review/173/)
  - Rate limits: orders 10/s and 250/min, live data 10/s and 300/min, WebSocket up to 1,000 subscriptions. Tokens expire daily (OAuth2, TOTP or API key + checksum flows). — [Chittorgarh](https://www.chittorgarh.com/broker/groww/api-for-algo-trading-review/173/); [AlgoTest Groww docs](https://docs.algotest.in/broker/groww/)
- **Brokerage context:** Angel One charges ₹0 for equity delivery and a flat ₹20 per executed order for intraday and F&O. — [StockCalc](https://stockcalc.in/calculator/brokerage/angel-one)

### Inferences
- For a buy-and-hold personal app:
  - **Zerodha Personal (free)** covers orders, GTT and holdings; free EOD prices can come from elsewhere (for example, exchange bhavcopy).
  - **Dhan and Angel** are also free for orders.
  - **Upstox's ₹10/order** fee is a real cost for frequent small SIP-like stock buys.
- Upstox's 1-year read-only Analytics token is useful for an on-device portfolio view without daily login.
- GTT orders (Zerodha) can be placed once and left to trigger server-side without a live session. This is a way to "automate" limit buys and stop-losses within the daily-login constraint (inferred from GTT being available in the free plan).

### Gaps
- **ICICI Breeze:** no current information on pricing, token rules or 2026 status.
- **Kite access token expiry time** (commonly 6 AM next day): not confirmed in this search session.
- **Angel One / Fyers:** official 2026 fee pages not reached.
- **Static IP:** whether any broker offers a hosted static-IP relay was not found. Whether brokers' terms forbid embedding API keys in a mobile app was not found either.

## 4. Mutual fund automation (SIP/STP/SWP, BSE StAR MF, MFU, MF Central, direct platforms, mandates)

### Takeaway
An individual **cannot directly join** the exchange MF order platforms. BSE StAR MF API access is granted to members and vendors, and direct-plan platforms are licensed "Execution Only Platforms" (EOPs) under SEBI's 2023 framework. Broker APIs such as Kite Connect expose MF holdings, orders and SIPs read-only but do not allow placement. The practical automation for a personal app is therefore:
- set up SIPs, STPs and SWPs once on a direct platform (Coin, Kuvera, Groww, MF Central) backed by an eNACH or UPI Autopay mandate;
- have the app read the results through CAS or broker read-APIs;
- deep-link the user into the platform for one-off lumpsum orders.

### Cited Findings
- **BSE StAR MF**
  - The Web Services API (v3.5, Aug 2023) supports SIP and X-SIP order entry. Access is granted **on request by a member or vendor**, and a mandate ID is mandatory for XSIP orders. — [BSE StAR MF API file structure PDF](https://www.bsestarmf.in/APIFileStructure.pdf); [BSE Xsip page](https://www.bseindia.com/Static/Markets/MutualFunds/Xsip.aspx)
  - The integration sequence is: register the client, create a mandate, then create SIPs. BSE auto-triggers each instalment, and multiple XSIPs can sit under one mandate up to its limit. — [utkarshohm/mf-platform-bse (GitHub)](https://github.com/utkarshohm/mf-platform-bse)
- **Execution Only Platforms (EOPs):**
  - Framework: SEBI circular **SEBI/HO/IMD/IMD-PoD-1/P/CIR/2023/86** (June 2023), effective **1 Sep 2023**. — [Taxmann](https://taxmann.com/post/blog/sebi-introduces-framework-for-execution-only-platforms-for-investing-in-direct-plans-of-mf-schemes)
  - **Category 1** EOPs are agents of AMCs, register with AMFI and may not charge investors. **Category 2** EOPs are agents of investors, register as stock brokers and operate through exchange platforms; they may charge investors capped fees. — [Taxmann](https://taxmann.com/post/blog/sebi-introduces-framework-for-execution-only-platforms-for-investing-in-direct-plans-of-mf-schemes); [AMFI EOP guidelines](https://www.amfiindia.com/eops/amfi-guidelines)
  - EOP-only exchange members must keep a ₹10 lakh base minimum capital deposit. — [Business Standard](https://www.business-standard.com/markets/mutual-fund/market-regulator-sebi-sets-deposit-requirement-for-execution-only-platforms-123100601157_1.html)
  - No 2025–26 revision of the EOP framework was found.
- **Kite Connect MF:** read-only endpoints; placement is not supported (see section 3). — [Kite MF docs](https://kite.trade/docs/connect/v3/mutual-funds/)
- **eNACH mandates:** Zerodha Coin caps eNACH mandates at **₹1 crore per day**. Many SIPs can link to one mandate, but one SIP cannot use several mandates. — [Zerodha support: SIP mandate on Coin](https://support.zerodha.com/category/mutual-funds/payments-and-orders/coin-mandates/articles/sip-mandate-on-coin)
- **UPI Autopay (recurring):**
  - The per-debit limit for MF SIPs, insurance and credit-card bills is **₹1,00,000** without re-authentication; the general category is ₹15,000. Pre-debit notification is required at least 24 hours before each debit. — [Zerodha TradingQnA: UPI autopay for SIPs](https://tradingqna.com/t/introducing-upi-autopay-for-sip-payments/173064); [RocketPay explainer](https://www.rocketpay.co.in/blog/what-is-upi-autopay-mandate)
  - An RBI proposal to exempt recurring MF payments up to ₹1 lakh from extra authentication was reported; its finalisation was not confirmed here. — [PersonalFN](https://www.personalfn.com/dwl/Mutual-Funds/no-more-otp-hassle-rbi-greenlights-hike-in-transaction-limit-for-upi-auto-payments)
- **UPI one-time (P2M capital markets):** From **15 Sep 2025**, NPCI raised the capital-markets category, which includes MFs, to **₹5 lakh per transaction** with a **₹10 lakh per 24h** cumulative cap (previously ₹2 lakh). The limit applies only to verified merchants, and banks may enforce lower limits. — [Cafemutual](https://cafemutual.com/news/industry/35768-investors-can-now-invest-up-to-rs10-lakh-in-mutual-funds-and-insurance-through-upi-apps); [Stable Money blog](https://stablemoney.in/blog/npci-raises-upi-limit); [Bandhan MF note via Angel One](https://www.angelone.in/news/mutual-funds/bandhan-mutual-fund-raises-upi-investment-limit-to-5-lakh-for-mutual-fund-transactions)

### Inferences
- Programmatic MF order placement for one individual is realistically out of reach without becoming, or partnering with, a registered entity (MFD with ARN, RIA, or a broker/EOP). It is not worth pursuing for a personal app.
- The app should model SIP, STP and SWP as **scheduled expectations** that it reconciles against CAS or holdings data, not as orders it places.
- Recommendations to change an SIP amount should end in a deep link to the user's platform. Keep the eNACH ceiling high (for example, ₹1 lakh or more) so step-ups need no new mandate.

### Gaps
- MF Central and MF Utilities (MFU): no information found on any public or individual API. Both appear to be portal or app only.
- Kuvera, Groww and Coin: no public MF order APIs found.
- Whether a 2026 NPCI or RBI circular changed the UPI Autopay ₹1 lakh no-AFA limit for MF SIPs is unconfirmed.

## 5. Data access: Account Aggregator, CAS statements, AMFI NAV files

### Takeaway
An individual **cannot** become an Account Aggregator Financial Information User (FIU). FIUs must be entities regulated by a financial-sector regulator and certified through Sahamati. AA data is only reachable by building on a licensed FIU or TSP partner, which is impractical for a personal offline app. The realistic personal-data rails are:
- **CAS PDFs:** CAMS and KFintech e-CAS or MF Central for mutual funds; NSDL or CDSL CAS for demat holdings.
- **Broker read APIs.**
- **AMFI's free daily NAVAll.txt.**

### Cited Findings
- **Who can be an FIU:**
  - An FIU must be an entity registered with, and regulated by, a financial sector regulator. Only RBI-registered NBFCs can be Account Aggregators. — [Sahamati participation terms (via Bajaj Housing Finance PDF)](https://www.bajajhousingfinance.in/documents/37350/53249/Account%20Aggregator%20Participation%20Terms%20-%20Sahamati.pdf); [Sahamati FIU page](https://sahamati.org.in/fiu/)
  - FIUs must pass Sahamati certification and comply with ReBIT technical standards, and must enrol in the UAT Central Registry before going live. TSPs such as Setu and Perfios handle integration. — [Sahamati FAQ](https://sahamati.org.in/faq/); [Setu FIU go-live docs](https://docs.setu.co/data/account-aggregator/licenses-and-go-live/go-live); [Sahamati TSPs](https://sahamati.org.in/tsp/fip-fiu-tsp/)
- **CAS for mutual funds (CAMS / KFintech / MF Central):**
  - AMFI lists CAMS (camsonline.com) and KFintech (mfs.kfintech.com) e-CAS request pages, plus MF Central as a joint portal. Users choose a Summary or Detailed CAS for a chosen period. — [AMFI CAS page](https://www.amfiindia.com/investor-corner/investor-center/cas.html); [AMFI download CAS](https://amfiindia.com/investor-corner/online-center/download-CAS.html)
  - CAS arrives as a password-protected PDF, commonly with the PAN or date of birth as password. — [Scripbox](https://scripbox.com/mf/consolidated-mutual-fund-statement/)
  - CAS is emailed monthly for accounts with transactions, plus half-yearly. — [InCred Money](https://www.incredmoney.com/knowledge-center/share-market/how-to-download-consolidated-account-statement-cas-step-by-step-instructions/)
- **CAS from depositories:** NSDL and CDSL issue consolidated statements covering demat holdings. Sources describe CAS issuers inconsistently. — [Canara HSBC Life](https://www.canarahsbclife.com/blog/investment/cas-statement-mutual-funds); [Share.market](https://www.share.market/buzz/learn/cas/)
- **AMFI NAVs:**
  - The full daily NAV file is free at `https://www.amfiindia.com/spages/NAVAll.txt`. It is semicolon-delimited with scheme code, ISIN, name, NAV and date. — [amfi-nav-extractor (GitHub)](https://github.com/fuzail-pixel/amfi-nav-extractor); [amfipy (PyPI)](https://pypi.org/project/amfipy/)
  - AMFI's official NAV download page serves history only **90 days at a time**. — [AMFI NAV download](https://www.amfiindia.com/net-asset-value/nav-download)

### Inferences
- For an offline-first Android app, the best design is:
  - **CAS PDF import** (user forwards or chooses the file; the app parses it on-device with the user-provided password);
  - a daily **NAVAll.txt** fetch for valuation;
  - optional broker read-APIs for stock holdings.
- AA should be treated as out of scope unless a licensed FIU partner offers an individual-consumer flow.

### Gaps
- casparser or similar open-source CAS parsers: not confirmed in this session (no search results).
- No information on whether CAMS or KFintech offer any individual-facing API.
- AA support for MF and equity FIPs (CAMS, KFintech, NSDL, CDSL as FIPs): not verified.

## 6. Fixed deposits: booking APIs, aggregators, DICGC cover

### Takeaway
There is no public, individual-usable FD booking API. Digital FD booking runs through B2B infrastructure such as **Upswing** (plug-and-play FD APIs for banks, NBFCs and fintechs). Consumer platforms such as **Stable Money** route the deposit straight to the partner bank. DICGC cover is still **₹5 lakh per depositor per bank** as of October 2026 and does not apply to NBFC FDs. So a personal app should treat FDs as **manually entered or imported** holdings. Recommendations can spread deposits across banks to stay under the ₹5 lakh cover.

### Cited Findings
- **DICGC cover:** Cumulative deposits, including FDs, are insured up to **₹5 lakh per bank per depositor**. This was still stated on pages updated 22–28 Oct 2026, and no 2026 increase was found. — [Paisabazaar (updated Oct 2026)](https://www.paisabazaar.com/fixed-deposit/); [Stable Money Play listing](https://play.google.com/store/apps/details?id=in.stablemoney.app&hl=en_IN); [Stable Money DICGC explainer](https://stablemoney.in/blog/all-about-dicgc-deposit-insurance-and-credit-guarantee-corporation)
- **Stable Money:**
  - It advertises FDs up to 8.6% and bonds at 9–12%, from ₹1,000.
  - The customer pays the bank directly, and the bank completes KYC and issues the receipt; Stable Money does not hold or pool funds. — [Stable Money site](https://stablemoney.in/); [Play listing](https://play.google.com/store/apps/details?id=in.stablemoney.app&hl=en_IN)
- **Upswing** builds B2B "plug-and-play APIs" that let banks, NBFCs and e-commerce players launch digital FDs, and it powers platforms including Stable Money and Wint Wealth. This is from a roughly 2025 trade article. — [The CapTable via Suryoday Bank PDF](https://suryoday.bank.in/cms/uploads/the_captable_com_inside_the_digital_fd_universe_how_fintechs_are_breathing_new_life_into_an_age_old_banking_product_f2569a3940.pdf)
- **Rates context:** In January 2026, small finance bank FD rates reached about 8%. — [Business Standard, 16 Jan 2026](https://www.business-standard.com/finance/personal-finance/fd-rates-touch-8-at-small-finance-banks-as-returns-outpace-big-lenders-126011600261_1.html)
- **NBFC FDs** (Bajaj Finance, Mahindra and similar) carry no DICGC cover. This is a secondary source. — [SarkariStep review](https://sarkaristep.com/stable-money-app-review-2026-safe-hai-ya-nahi-fd-interest-rate/)

### Inferences
- FD automation should mean: maturity reminders, a "ladder" planner, TDS projection and DICGC-concentration warnings. Booking itself happens in the bank or aggregator app.
- The app's FD recommendations should enforce a per-bank ₹5 lakh exposure check that includes savings balances at the same bank. The cover is per depositor per bank, across deposit types.

### Gaps
- Upswing's current (2026) API terms and whether any individual access exists: not found.
- No bank found with a retail-customer FD-booking API.
- No primary DICGC or RBI source fetched. The ₹5 lakh figure rests on consistent secondary pages updated October 2026.

## 7. Tax rules that constrain trade recommendations (post-Budget 2024, Budget 2025/2026, Income-tax Act 2025)

### Takeaway
- **Listed equity and equity-oriented MFs:**
  - STCG (held 12 months or less) is taxed at **20%**.
  - LTCG is taxed at **12.5% above ₹1.25 lakh per year**, with no indexation. These rates apply to transfers from 23 Jul 2024 and were unchanged by Budget 2026.
- **Debt-oriented "specified mutual funds"** (more than 65% in debt or money market) are taxed at **slab rate whatever the holding period**.
- **The Income-tax Act 2025 replaced the 1961 Act from 1 April 2026.** It introduces a single "tax year" and renumbers everything:
  - 111A becomes **s.196**;
  - 112A becomes **s.198**;
  - 80C becomes **s.123** (instruments listed in Schedule XV);
  - the specified-MF definition is in **s.76(5)(b)**.
- **Budget 2026** (1 Feb 2026):
  - raised F&O STT from 1 Apr 2026;
  - limited the SGB tax exemption to original-issue bonds held to maturity;
  - moved buyback proceeds to capital-gains treatment.

The engine's tax data files need a 1961-Act set for tax years up to FY 2025-26 and a 2025-Act set from tax year 2026-27.

### Cited Findings
- **Budget 2024 equity rates:** For transfers on or after 23 Jul 2024, LTCG on listed equity and equity MFs is taxed at 12.5% above ₹1.25 lakh, and STCG at 20%. F&O STT was raised at the same time. — [Business Standard, 23 Jul 2024](https://www.business-standard.com/amp/markets/news/budget-2024-hikes-ltcg-tax-rate-to-12-5-stcg-to-20-stt-on-f-o-also-up-124072300553_1.html); [Tax2Win s.112A guide](https://tax2win.in/guide/section-112a-income-tax-ltcg-exemption)
- **Income-tax Act 2025 structure:** In force from **1 April 2026**. It cut 819 sections to 536 and 47 chapters to 23, and replaced the "previous year / assessment year" pair with a single "Tax Year". — [Wikipedia: Income-tax Act, 2025](https://en.wikipedia.org/wiki/Income-tax_Act,_2025); [SKAG transition guide](https://skagandassociates.com/resources/income-tax-act-2025/)
- **New sections for equity gains:**
  - **s.196** is the successor to 111A: 20% plus cess on STCG from STT-paid listed equity, equity-oriented fund units and business-trust units held 12 months or less.
  - **s.198** is the successor to 112A: 12.5% on LTCG above ₹1,25,000, no indexation, with the STT condition. — [Ebizfiling: s.196 and s.198](https://ebizfiling.com/blog/section-196-and-198-short-term-and-long-term-capital-gains/); [MyITReturn help: holding periods and rates under ITA 2025](https://help.myitreturn.com/hc/en-us/articles/60943188924313-Capital-Gains-Holding-Periods-Tax-Rates-Income-Tax-Act-2025); [CAclubindia](https://www.caclubindia.com/articles/long-term-capital-gain-on-shares-as-per-income-tax-act-2025-56368.asp)
  - At Bill stage this was "Clause 198", which mirrored 112A(1). — [TaxTMI note](https://www.taxtmi.com/tmi_notes?id=1655)
- **Charging section** for capital gains (old s.45) is reported as new **s.67**, from secondary mapping tables only. Another table maps old s.112 to s.197 and old s.54 to s.82. These are not verified. — [Tax2Win mapping](https://tax2win.in/guide/income-tax-act-2025-section-mapping-old-vs-new); [Mayur Agarwal mapping tool](https://www.mayuragr.com/income-tax-act-2025-mapping/); [TaxGarden mapping](https://taxgarden.in/blog/old-vs-new-income-tax-sections-2026-mapping-table)
- **80C becomes s.123:**
  - The ₹1.5 lakh limit is unchanged, with qualifying instruments (ELSS, PPF, EPF, SSY, NSC, LIC, ULIP) moved to **Schedule XV**. — [Business Today, 24 Mar 2026](https://www.businesstoday.in/personal-finance/tax/story/income-tax-act-2025-section-80c-becomes-section-123-from-april-1-2026-heres-what-taxpayers-must-know-522120-2026-03-24); [Business Standard on I-T Bill](https://www.business-standard.com/amp/finance/personal-finance/i-t-bill-2025-deductions-under-80c-moves-to-section-123-see-changes-125021700699_1.html); [Upstox news](https://upstox.com/news/personal-finance/tax/section-80-c-changes-to-section-123-in-new-tax-bill-deductions-include-ssy-nps-epf-elss-nsc-lic-ulip/article-145930)
  - The ELSS deduction is available only under the **old regime**; the new regime is the default. — [Tata Capital ELSS](https://www.tatacapital.com/blog/wealth-services/elss-funds/)
- **Specified mutual fund (debt):**
  - Definition: a fund investing more than 65% in debt and money market instruments, or a fund of funds investing 65% or more in such funds. Gains are taxed at slab rate regardless of holding period. — [TaxManagementIndia](https://taxmanagementindia.com/web/tmi_highlights_details.asp?id=80053); [Hindustan Times via Magzter, Apr 2025](https://www.magzter.com/ja/stories/newspaper/Hindustan-Times-Patna/MFS-ENGINEER-CATEGORY-FOR-TAXEFFICIENT-DEBT-FUNDS)
  - Effective date: one source dates the 65% redefinition from 1 Apr 2026 (AY 2026-27), not 2025. — [TaxManagementIndia blog](https://taxmanagementindia.com/web/tmi_blog_details.asp?id=762692)
  - In the 2025 Act the definition sits in **s.76(5)(b)**, measured by the annual average of daily closing figures. — [Indian Kanoon](https://future.indiankanoon.org/doc/72377898)
  - Budget 2026 did not restore debt-fund parity. — [IIFL: Budget 2026-27 implications for MFs](https://www.indiainfoline.com/blog/union-budget-2026-27-implications-for-mutual-funds)
- **Budget 2026 (1 Feb 2026):**
  - Equity-fund taxation unchanged (STCG 20%; LTCG 12.5% above ₹1.25 lakh). The exemption was **not** raised to ₹2 lakh. — [Tata MF: post-Budget 2026 tax map](https://www.tatamutualfund.com/blogs/post-budget-2026-new-tax-map-equity-investors); [IIFL](https://www.indiainfoline.com/blog/union-budget-2026-27-implications-for-mutual-funds)
  - STT on futures rose from 0.02% to 0.05%, and on options from 0.10% to 0.15%, effective 1 Apr 2026. — [Business Standard: Budget 2026 impact](https://www.business-standard.com/budget/news/how-budget-2026-impacts-your-investments-stocks-gold-and-mutual-funds-126020200254_1.html)
  - **SGBs:** the redemption exemption is now limited to bonds subscribed at original issue and held to maturity. Secondary-market buyers pay LTCG at 12.5% (or STCG at slab rate). Gold ETFs are unchanged. — [KPMG FS POV on Budget 2026-27](https://assets.kpmg.com/content/dam/kpmgsites/in/pdf/2026/02/financial-services-pov-union-budget-2026-27.pdf); [PrimeInvestor](https://primeinvestor.in/reports/union-budget-2026/); [Business Standard](https://www.business-standard.com/budget/news/how-budget-2026-impacts-your-investments-stocks-gold-and-mutual-funds-126020200254_1.html)
  - **Buybacks** move from dividend treatment to capital gains, with additional buyback tax for promoters (22% corporate, 30% non-corporate). How this applies to ordinary shareholders is described inconsistently. — [Tata MF](https://www.tatamutualfund.com/blogs/post-budget-2026-new-tax-map-equity-investors)
- **Annual LTCG harvesting** of the ₹1.25 lakh exemption is a discussed strategy. — [Upstox news: should you book ₹1.25 lakh LTCG every year](https://upstox.com/news/personal-finance/tax/should-you-book-1-25-lakh-long-term-capital-gains-ltcg-every-year/article-169100/)

### Inferences
These tax rules translate into design constraints for the recommendation engine.
- **Tax-year-versioned data files:**
  - FY 2025-26 and earlier: 1961 Act with s.111A, s.112A, s.80C.
  - Tax year 2026-27 onward: 2025 Act with s.196, s.198, s.123.
  - Rates are unchanged across the switch, but citations and labels change. This matches the AGENTS.md rule that rates and section numbers live in versioned data.
- **Engine features this supports:**
  - Flag sells within 12 months of purchase (20% vs 12.5%).
  - Track how much of the ₹1.25 lakh exemption has been used, and suggest gain harvesting near year end.
  - Apply FIFO lot selection (standard for MF units and demat holdings).
  - Respect the 3-year ELSS lock-in per SIP instalment.
  - Treat debt-fund switches as slab-taxed events.
  - Apply the SGB "original issue, held to maturity" condition.

### Gaps
- **Grandfathering:** the 31 Jan 2018 FMV rule (old s.55(2)(ac) / s.112A) and its new-Act section number were not found. Its continuation under the 2025 Act is likely but unconfirmed.
- **Holding periods:** the 12/24-month split for listed and unlisted assets was not confirmed in this session. The 3-year ELSS lock-in per instalment was not confirmed either.
- **Section numbers:** the official CBDT mapping table and the enacted Act text were not read. All new-Act section numbers (196, 198, 123, 76(5)(b), 67) rest on secondary sources and need verification against incometaxindia.gov.in.
- **Pending changes:** no information on any October 2026 proposals ahead of Budget 2027.
- **Specified-MF effective date:** the conflict (1 Apr 2025 vs 1 Apr 2026, i.e. AY 2026-27) is unresolved.
