# MVP Roadmap

## Phase 0 — Foundations (done in this pass)
- [x] Schema (yours) + additive `scoring_rules` table
- [x] Deterministic scoring engine (`BandScorer`, `MetricCalculator`, `ScoringEngine`, `FlagDeductionRules`)
- [x] Bounded AI layer (`ClaudeAiService`) for flag extraction + narrative only
- [x] REST API (companies, reports, what-changed, compare)
- [x] Demo seed data (HDFC Bank, ICICI Bank, TCS) so the API is exercisable without a live scrape
- [x] React/TS frontend shell: search, report view, comparison view

## Phase 1 — Make the scraper real
**v2 status:** items 3 and 4 done (pytest suite against a fixture; FastAPI service called before every report). Items 1–2 remain: replace the hand-built fixture with a saved live page and adjust row labels if needed.

The scraper module ships as a structured skeleton, not a finished scraper —
Screener's and NSE's exact markup/label text need verifying against the
live sites (not guessable reliably from memory, and both sites are outside
this build environment's network access). Concretely:
1. Fetch one real Screener company page, save it as a fixture, and write
   the `row_label -> column` mapping in `_upsert_parsed_screener_data`
   against that fixture.
2. Same for NSE's price-history and announcements JSON shape.
3. Add a small pytest suite that runs the parser against saved fixture
   HTML/JSON (not live requests) so scraper changes don't require hitting
   the real sites to verify.
4. Wire `main.py` into a scheduler or an internal FastAPI endpoint the Java
   API calls before `POST /reports/generate/{id}` if data is stale.

## Phase 2 — Fill the metric gaps documented in `MetricCalculator`
**v2 status:** interest coverage was already closed; debt-to-equity is now scraped. Pledged shares, dividend history and customer concentration remain.

Several metrics from the scoring design are marked "not computable from
current schema" — not fudged, just absent so weights renormalize around
them. Closing these raises confidence scores across the board:
- `interest_coverage` — needs an `interest_expense` column on `financials`.
- `pledged_share_pct` — needs a column on `shareholding_pattern`.
- Proper `dividend_years_paid_of_5` — needs a per-year dividend-payment
  table instead of the point-in-time `dividend_yield` proxy currently used.
- `customer_concentration` — likely needs to become a qualitative AI flag
  (annual-report text extraction) rather than a numeric metric, since
  Screener doesn't expose it either.

## Phase 3 — Sector-conditional bands — **done in v2**
`scoring_rules.sector`; see `ScoringEngine.effectiveRules`. Two example overrides are seeded; add more per sector as needed.

Right now every company is scored against the same D/E, ROCE, etc. bands.
A capital-intensive utility and an IT services company shouldn't share a
debt-to-equity rubric. Extend `scoring_rules` with an optional
`sector` column (null = default/fallback band) and have `ScoringEngine`
prefer a sector-specific row when one exists.

## Phase 4 — "What changed?" polish — **evidence diff done in v2**
Remaining: an AI explanation of *why* a category moved, grounded only in the diff.

Current implementation diffs the two most recent reports' category scores.
Natural next steps: surface which individual `report_evidence` rows are new
vs. carried over, and let the AI narrative layer explain *why* a category
moved (still grounded only in the score delta + evidence diff, never new
invented facts).

## Phase 5 — Company comparison, N-way — **done in v2** (2–6 companies)

Current `/compare` is pairwise. Extending to 3+ companies is mostly a
frontend table-layout problem once the pairwise service logic is
generalized to N companies instead of exactly A/B.

## Phase 6 — Auth, multi-user, rate limits on your own API
**v2 status:** single-user watchlist and report reuse/de-duplication (limits scrape traffic) done. Auth and per-user data remain; move `ReportCache` to Redis for multiple instances.

Needed before this stops being a single-user tool: user accounts, per-user
watchlists (a natural home for cached "what changed?" over time), and
rate-limiting your own `/reports/generate` endpoint so one user can't
trigger unbounded scrape traffic against Screener/NSE.

## Suggested build order if you're doing this solo
1. Get Postgres running locally, run `database/schema.sql` then
   `database/seed_scoring_rules.sql` then `database/seed_demo_companies.sql`.
2. Get the Spring Boot app running against that seed data — you should be
   able to `POST /reports/generate/1` and get a real report back for HDFC
   Bank using only the demo data, before writing a single line of real
   scraper code. This is the fastest way to sanity-check the scoring engine
   end-to-end.
3. Get the frontend talking to that API.
4. Only then invest in Phase 1 (real scraping) — it's the most externally
   fragile part (site markup changes, bot detection, rate limits) and the
   least valuable to build first if the rest of the pipeline isn't proven.
