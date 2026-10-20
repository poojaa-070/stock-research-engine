# Scoring Formula Design

## 1. Core principle: everything reduces to a `BandScorer`

Almost every raw metric (ROCE, D/E, PEG, promoter holding delta, RSI...) needs the
same transformation: **raw value -> 0-100 score**, via piecewise-linear
interpolation between defined bands. Instead of writing bespoke if/else per
metric, we define one reusable primitive and feed it a config table.

```
score(value, bands) where bands = [(threshold_1, score_1), (threshold_2, score_2), ...]
```

Bands are ordered by threshold. We linearly interpolate between the two
bracketing points; below the lowest we clamp to its score, above the highest
we clamp to its score. Some metrics are "lower is better" (D/E, PE) and some
are "higher is better" (ROCE, revenue growth) — the bands table just encodes
this by ordering scores accordingly, no special-casing needed in the scorer.

This is why we added a `scoring_rules` table (new, additive — does not change
your existing schema) so the bands are **data, not code**. You can retune
"what counts as a good ROCE" without a redeploy:

```sql
CREATE TABLE scoring_rules (
    id SERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,      -- 'financial_health' | 'growth' | ...
    metric_key VARCHAR(50) NOT NULL,    -- 'roce' | 'debt_to_equity' | ...
    band_thresholds NUMERIC[] NOT NULL, -- e.g. {5, 10, 15, 20}
    band_scores NUMERIC[] NOT NULL,     -- e.g. {15, 40, 65, 85, 100} (n+1 scores for n thresholds)
    weight_within_category NUMERIC NOT NULL,
    higher_is_better BOOLEAN DEFAULT true,
    active BOOLEAN DEFAULT true,
    UNIQUE (category, metric_key)
);
```

Each category score = weighted average of its metric sub-scores. Overall
score = weighted average of category scores (weights from your brief).

## 2. Category-by-category bands (seed defaults — tune later)

These are the starting values we seed into `scoring_rules`. All numbers are
illustrative starting points for Indian large/mid-cap equities — expect to
retune per-sector later (a capital-intensive utility should not be scored on
the same D/E bands as an IT services company; sector-conditional bands are a
v2 feature, see roadmap).

### 2.1 Financial Health (20%)
| Metric | Weight | Bands (threshold -> score) | Direction |
|---|---|---|---|
| Debt-to-Equity | 30% | 0→100, 0.5→85, 1→60, 2→35, 3+→10 | lower better |
| Interest Coverage Ratio (EBIT/Interest) | 25% | <1→5, 2→30, 4→60, 8→85, 15+→100 | higher better |
| OCF/Net Profit ("cash conversion") | 25% | <0.5→10, 0.8→40, 1→70, 1.2→90, 1.5+→100 | higher better |
| FCF trend (5yr slope, normalized) | 20% | negative→20, flat→50, positive→80, strongly positive→100 | higher better |

*Interest coverage and D/E together catch the "profitable but drowning in debt"
case explicitly called out in your brief.*

### 2.2 Business Quality (20%)
| Metric | Weight | Bands | Direction |
|---|---|---|---|
| ROCE (latest) | 30% | <5→15, 10→40, 15→65, 20→85, 25+→100 | higher better |
| ROCE trend (5yr, improving vs declining) | 20% | declining→20, flat→50, improving→85 | higher better |
| Operating margin stability (stdev over 5yr, normalized) | 20% | high volatility→30, moderate→60, stable→90 | lower volatility better |
| Qualitative moat/management-quality flags (see §3) | 30% | derived from bounded AI-flag scoring | n/a |

### 2.3 Growth (15%)
| Metric | Weight | Bands | Direction |
|---|---|---|---|
| Revenue CAGR (3yr) | 35% | <0→20, 5→45, 10→65, 15→85, 20+→100 | higher better |
| Net Profit CAGR (3yr) | 35% | <0→15, 5→40, 10→65, 15→85, 20+→100 | higher better |
| EPS CAGR (3yr) | 30% | <0→15, 5→40, 10→65, 15→85, 20+→100 | higher better |

### 2.4 Valuation (20%) — the trickiest, needs peer + historical context
| Metric | Weight | Bands | Direction |
|---|---|---|---|
| PE vs own 5yr historical median (ratio) | 30% | 1.5x→20, 1.2x→45, 1x→70, 0.8x→90, 0.6x→100 | lower ratio better |
| PE vs sector peer median (ratio) | 25% | same shape as above | lower ratio better |
| PEG ratio | 20% | >2→20, 1.5→45, 1→70, 0.7→90, 0.5→100 | lower better |
| EV/EBITDA vs peer median (ratio) | 25% | same shape as PE-vs-peer | lower ratio better |

*Valuation is explicitly relative (vs own history, vs peers) — a raw PE of 40
means nothing without that context, which is why "peer comparison" is baked
into the metric itself, not bolted on as a separate feature.*

### 2.5 Management (10%)
| Metric | Weight | Bands | Direction |
|---|---|---|---|
| Promoter holding trend (pct-point change over 3yr) | 40% | falling >5pp→15, falling→40, flat→70, rising→90, rising >5pp→100 | higher better |
| Pledged-share pct of promoter holding | 30% | >50%→10, 25%→35, 10%→65, 0%→100 | lower better |
| Dividend consistency (paid in N of last 5 years) | 30% | 0/5→30, 2/5→50, 4/5→75, 5/5→100 | higher better |

### 2.6 Risk (10%) — inverted framing: this category scores "safety", not "danger"
| Metric | Weight | Bands | Direction |
|---|---|---|---|
| Debt risk (same D/E bands as 2.1, reused) | 30% | — | lower D/E better |
| Customer/revenue concentration (top-5 customer % of revenue, if disclosed) | 25% | >50%→20, 30%→50, 15%→80, <10%→100 | lower better |
| Sector regulatory-risk flag (qualitative, see §3) | 25% | bounded AI-flag scoring | n/a |
| Commodity/input-cost exposure flag (qualitative, see §3) | 20% | bounded AI-flag scoring | n/a |

*Concentration data often isn't in Screener's standard tables — mark as
`null`/"data not available" rather than guessing; a category average should
skip nulled metrics and renormalize remaining weights, not silently treat
missing as zero or 50.*

### 2.7 Momentum (5%, optional layer)
| Metric | Weight | Bands | Direction |
|---|---|---|---|
| Price vs 200-DMA (%) | 40% | <-15%→10, -5%→40, 0%→60, +5%→80, +15%+→100 | higher better |
| 50-DMA vs 200-DMA (golden/death cross proximity) | 30% | death cross→20, converging→50, golden cross→90 | higher better |
| RSI(14) | 30% | <30→70 (oversold, often scored neutral-positive), 30-70→ scaled to 50-70, >70→30 (overbought) | non-linear, U-shaped-ish |

## 3. Where the AI layer fits (and its hard boundary)

Some inputs are inherently qualitative: moat strength, management-quality
narrative signals, sector regulatory risk, commodity exposure. The brief is
explicit that the AI must not invent the *numbers*. The pattern:

1. Claude reads scraped text (annual report excerpts, concall summaries, news
   headlines already in `news_items`) and outputs a **bounded structured
   flag**, e.g. `{"flag": "customer_concentration_risk", "present": true,
   "confidence": "medium", "evidence_quote": "..."}` — never a raw score.
2. Java-side rule-based logic converts flags to points deterministically
   (e.g. `present=true` on a negative flag → -15 points off that metric's
   100 baseline; `confidence=low` halves the deduction). This mapping table
   lives in code/config, not in the prompt.
3. Every flag becomes a `report_evidence` row with its source, so the
   qualitative 30% of Business Quality is still fully auditable — "why did
   this company lose 15 points on moat?" always has a traceable answer.

This keeps the non-negotiable invariant from your brief: **the score is a
deterministic function of stored facts; Claude explains and extracts
structured flags, it never assigns the final number.**

## 4. Confidence score

`analysis_reports.confidence` (already in your schema) is populated
separately from the scores: it's a function of **data completeness**, not
data quality — e.g. `confidence = (# non-null metrics used) / (# metrics
expected for this category set)`. A company with missing quarterly data or a
failed scrape still gets a report, but with visibly lower confidence and a
"data gaps" note, rather than silently defaulting missing metrics to
mid-scale values that would masquerade as real evidence.

## 5. Worked example (illustrative numbers)

```
Financial Health:
  D/E = 0.4        -> band(0→100, 0.5→85) interpolate -> ~88
  Interest Cov = 6  -> band(4→60, 8→85) interpolate    -> ~75
  OCF/NP = 1.1      -> band(1→70, 1.2→90) interpolate  -> ~80
  FCF trend = positive                                  -> 80
  Weighted: 0.30*88 + 0.25*75 + 0.25*80 + 0.20*80 = 81.15

... (other categories similarly) ...

Overall = FinHealth(81)*0.20 + BizQual(74)*0.20 + Growth(68)*0.15
        + Valuation(55)*0.20 + Mgmt(72)*0.10 + Risk(80)*0.10 + Momentum(60)*0.05
        = 16.2 + 14.8 + 10.2 + 11.0 + 7.2 + 8.0 + 3.0 = 70.4

70.4 -> "🟢 Worth Researching" (see verdict bands below)
```

## 6. Verdict bands (maps overall_score -> your fixed labels)

| Score range | Verdict |
|---|---|
| 80-100 | 🟢 Strong Candidate |
| 65-79.9 | 🟢 Worth Researching |
| 50-64.9 | 🟡 Neutral–Watch |
| 35-49.9 | 🟠 High Risk |
| 0-34.9 | 🔴 Weak Fundamentals |

Verdict is a pure lookup on `overall_score` — no separate logic, no AI
involvement, so it can never contradict the number shown next to it.

---

## 7. Update: full professional-investor framework coverage

The bands above were the original design. This section documents what
changed to cover the complete checklist a professional investor works
through (profitability/efficiency, financial strength/debt, valuation,
growth, moat, governance, industry structure, risk) — and what's
deliberately still out of scope.

### 7.1 New hard-data metrics (schema extended)

`financials` gained three columns: `interest_expense`, `current_assets`,
`current_liabilities`. This enables two metrics that were previously
documented as "not computable":

- **Interest Coverage Ratio** = Operating Profit / Interest Expense
  (Financial Health, 20%)
- **Current Ratio** = Current Assets / Current Liabilities
  (Financial Health, 15%)

Both are left `null` — not scored, not guessed — for banks/NBFCs, since
neither concept applies the same way to a financial institution's balance
sheet as it does to a manufacturing or IT services company.

**Net Debt / EBITDA** (15%) uses `operating_profit` as an EBITDA proxy —
flagged as a proxy rather than a true EBITDA figure, since Screener's
"Operating Profit" line may already net out D&A depending on how a company
reports.

**FCF CAGR** replaces the old direction-only `fcf_trend` (was just -1/0/+1)
with an actual growth-rate percentage, reusing the same CAGR machinery as
revenue/profit/EPS growth. It's shared between Financial Health (15%) and
Growth (15%) — a company with strong, growing free cash flow is both
financially healthy *and* growing, and the metric earns its weight in both
places rather than being arbitrarily assigned to just one.

**P/B ratio** (Valuation, 15%) — added because P/E alone is a weak signal
for banks and other asset-heavy businesses, where price-to-book is the more
standard multiple.

**Promoter holding level** (Management, 20%) — previously only the *trend*
in promoter holding was scored. A company at 65% promoter holding and one
at 15% aren't equally exposed to a promoter exit even with identical
trends, so the absolute level now gets its own weight alongside the delta.

### 7.2 Qualitative flags: from one shared pool to three, correctly isolated

**Before:** Business Quality had one qualitative flag pool. Risk had *two*
separate pools (`regulatory_flag`, `commodity_exposure_flag`), and both
pools independently applied deductions from the *entire* flag list — so
every risk-relevant flag silently counted twice against the Risk score.

**Now:** every flag belongs to exactly one of three pools —
`FlagDeductionRules.FlagCategory`: `BUSINESS_QUALITY`, `MANAGEMENT`, `RISK`
— and each category's single qualitative slot only applies flags tagged
for that category. No flag can influence two categories, and no category
double-applies the same flag twice.

The flag vocabulary itself grew from 10 to 19 keys, organized by which part
of the professional checklist they represent:

| Flag | Category | Effect |
|---|---|---|
| `moat_erosion` | Business Quality | -20 |
| `market_share_gain` | Business Quality | +10 |
| `durable_pricing_power` | Business Quality | +12 |
| `network_effects_present` | Business Quality | +10 |
| `high_switching_costs` | Business Quality | +10 |
| `cost_leadership_advantage` | Business Quality | +10 |
| `strong_intangible_assets` | Business Quality | +8 |
| `large_addressable_market` | Business Quality | +8 |
| `intense_competitive_rivalry` | Business Quality | -12 |
| `high_threat_of_substitutes` | Business Quality | -10 |
| `management_credibility_concern` | Management | -25 |
| `management_capital_allocation_strength` | Management | +10 |
| `related_party_transaction_flag` | Management | -20 |
| `auditor_qualification` | Management | -30 |
| `customer_concentration_risk` | Risk | -15 |
| `regulatory_overhang` | Risk | -15 |
| `commodity_cost_exposure` | Risk | -12 |
| `high_supplier_bargaining_power` | Risk | -10 |
| `high_buyer_bargaining_power` | Risk | -10 |

The first 10 map directly onto the moat framework (network effects,
switching costs, cost leadership, intangible assets) plus Porter's Five
Forces (rivalry, substitutes; supplier/buyer power live under Risk since
they're more "what could squeeze margins" than "how good is this
business"). The Management four cover the governance checklist
(credibility, capital allocation, related-party dealing, audit quality).

### 7.3 The "Key Ratios" panel — raw numbers, not scores

A new `keyRatios` block on the report response (and a matching UI panel)
surfaces the actual metric values — ROE %, ROCE %, D/E, Current Ratio,
Interest Coverage, P/E, P/B, PEG, EV/EBITDA, dividend yield, EPS/revenue
CAGR, promoter holding % and trend — independent of the 0–100 scoring
engine. This is deliberately a separate code path (`ReportService.
buildKeyRatios`) so a bug in the raw-number display can never silently
affect a score, and vice versa.

### 7.4 Deliberately NOT implemented (and why)

- **DCF / intrinsic-value modeling.** A real DCF needs assumed future growth
  rates and a discount rate. Having the AI generate those assumptions and
  present a resulting "fair value" would be exactly the kind of
  AI-predicts-the-future output the project brief rules out — it's a
  forecast wearing a valuation-methodology costume. Historical relative
  valuation (§2.4, PE/EV vs own history and peers) is the deterministic
  substitute already in place.
- **Sum-of-the-parts valuation.** Useful for conglomerates, but a genuine
  scope/complexity tradeoff for now — noted in the roadmap, not attempted.
- **FCF payout ratio / share buyback yield as numeric metrics.** Both need
  data this schema doesn't have (dividend-per-share history, shares
  outstanding over time) and Screener's standard tables don't expose them
  either. Buyback discipline is instead captured qualitatively via the
  `management_capital_allocation_strength` flag.
- **Accrual ratio as a distinct metric.** Conceptually already covered by
  `cash_conversion` (operating cash flow / net profit) — a company with
  high net income but weak cash generation shows up there.

---

## 8. v2 changes (0.2.0)

The design above is unchanged; these correct how it was applied. Full list in `CHANGELOG.md`.

1. **Non-finite values are never scored.** CAGR of a figure that turned
   negative is recorded as −100% (the lowest band); v1 produced `NaN`, which
   `BandScorer` placed in the best band. `BandScorer` now throws on
   non-finite values and on non-ascending thresholds.
2. **`margin_stability` thresholds are ascending:** `{0.03,0.08,0.15}` →
   `{90,90,60,30}`. v1's descending seed inverted the metric.
3. **Qualitative baseline is neutral.** Each `qualitative_flags` rule's
   `band_scores[0]` (60) is where its flag pool starts. Negative flags
   deduct, positive flags add, clamped to [0, 100]. If flag extraction
   didn't run (no API key, API error, no text to read), the slot is missing
   data: the category renormalises around it and confidence drops.
4. **Empty categories are excluded from the overall** (§4's "skip nulled
   metrics and renormalise" rule, applied one level up).
5. **Leverage metrics are skipped for financial institutions** (sector or
   industry mentions bank, NBFC, finance, insurance, lending), consistent
   with the existing treatment of interest coverage and current ratio.
6. **`*_cagr_3y` uses a ~3-year window**, the annual row nearest to three
   years before the latest. Shorter histories use what exists, and the
   evidence line states the real span.
7. **RSI is Wilder's RSI(14)**, shared by the Momentum score and the
   Technical Setup panel through one implementation.
8. **Sector overrides (§2 "v2 feature", now built):** a `scoring_rules` row
   with a `sector` replaces the default row of the same category and
   metric for that sector. Evidence lines note "(… bands)" when one applied.

Worked example of (3): a company with one high-confidence
`durable_pricing_power` flag and nothing else scores 60 + 12 = 72 on
Business Quality's qualitative slot (v1: min(100, 100 + 12) = 100, the same
as a company with no evidence at all).
