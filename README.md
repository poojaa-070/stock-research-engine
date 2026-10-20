# Stock Research Engine

An evidence-backed research tool for NSE/BSE companies. Every score is a
deterministic function of stored facts; the AI layer explains and extracts
structured flags, it never assigns the final number. See
`docs/SCORING_FORMULA.md` for exactly how, or the **Methodology** page in the
app, which is read live from the same rules.

```
database/   Postgres schema, seed data, and migrations/ for upgrading
backend/    Java 21 + Spring Boot — deterministic scoring engine + REST API
scraper/    Python — Screener.in + NSE scrapers, exposed as a FastAPI service
frontend/   React + TypeScript + Vite — search, report, compare, watchlist, methodology
docs/       Scoring formula, API design, roadmap
```

What's new in 0.2.0 (scoring fixes, watchlist, N-way compare, what-changed,
sector bands, charts, export, tests, Docker) is in **`CHANGELOG.md`**.

## Quickest start: Docker

```bash
cp .env.example .env          # optionally add ANTHROPIC_API_KEY
docker compose up --build
```

Open http://localhost:5173. The database is created and seeded with three
demo companies on first start. `docker compose down -v` resets it.

## Running each piece yourself

### 1. Database
```bash
createdb stock_research
psql stock_research -f database/schema.sql
psql stock_research -f database/seed_scoring_rules.sql
# demo data (optional):
psql stock_research -f database/seed_demo_companies.sql
psql stock_research -f database/seed_demo_prices.sql
psql stock_research -f database/seed_demo_history.sql
```

**Upgrading a 0.1.0 database?** Don't re-create it. Run:
```bash
psql stock_research -f database/migrations/001_fix_bigint_columns.sql   # only if created from a pre-BIGSERIAL schema
psql stock_research -f database/migrations/002_v2_enhancements.sql
psql stock_research -f database/seed_demo_history.sql                   # optional, demo companies only
```
Migration 002 is idempotent.

### 2. Backend
```bash
cd backend
export DB_USER=stock_research DB_PASSWORD=changeme
export ANTHROPIC_API_KEY=sk-ant-...   # optional, see below
mvn spring-boot:run                   # needs Maven 3.9+ and JDK 21
mvn test                              # 27 unit tests
```
Then `curl http://localhost:8080/api/health` and
`curl http://localhost:8080/api/reports/latest/1`.

Settings live in `backend/src/main/resources/application.yml`; the useful
environment variables are `DB_URL`, `DB_USER`, `DB_PASSWORD`,
`SCRAPER_BASE_URL`, `ANTHROPIC_API_KEY`, `CORS_ALLOWED_ORIGINS` and
`WATCHLIST_REFRESH_CRON`.

**Without `ANTHROPIC_API_KEY`** the deterministic scores still work. The
qualitative flag slots are counted as missing data (lower confidence, never
a free pass), and the summary and bull/bear case are written by a fixed
template from the computed evidence. The report says so on screen.

### 3. Scraper service (optional, for live data)
```bash
cd scraper
pip install -r requirements.txt
uvicorn api_server:app --port 8000
# tests:
pip install -r requirements-dev.txt
pytest                                           # parser + API tests
TEST_DB_NAME=stock_research_test pytest          # also the database tests
```
Without it, reports are generated from whatever is already stored and the
header dot turns amber.

### 4. Frontend
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
npm test           # 13 page/unit tests
```
The API base URL defaults to `http://localhost:8080/api` (override with
`VITE_API_BASE_URL`). **There is no bundled demo/fixture data.** Every
screen calls the real backend, and a failure shows the backend's own error
message rather than substituting fake numbers.

## What's real vs. still a skeleton

| Layer | Status |
|---|---|
| Schema, scoring engine, API | Complete; matches `docs/SCORING_FORMULA.md`. Unit-tested. |
| Frontend | Complete; builds clean; page tests against mocked API. |
| Screener.in / NSE scrapers | HTTP, rate-limiting, parsing and DB writes are real and tested against a **hand-built** HTML fixture shaped like Screener's pages. Row labels and NSE JSON field names still need confirming against the live sites: save a real page over `scraper/tests/fixtures/screener_company.html` and run `pytest`. |
| Not implemented, deliberately | DCF / intrinsic value (requires forecasting), pledged-share %, per-year dividend history, customer concentration. See `docs/SCORING_FORMULA.md` §7.4. |

## Live data: price, sector peers, and compare

Three things bypass the database on purpose and hit the market directly:

- **Current price**: `GET /quote/{symbol}` on the scraper hits NSE's live
  quote endpoint. The report shows a **LIVE** badge, the day's change and
  range when it worked, and falls back to the last scraped close otherwise.
- **Sector peers**: `GET /peers/{symbol}` scrapes Screener's peer table live.
  Peers not yet in the database get an **Analyse** button that imports them.
- **Compare two companies by name**: `GET /api/compare/live?a=&b=` imports
  and scores each one, then compares. For more than two, use the Compare
  page (`GET /api/compare/multi?ids=`).

All degrade gracefully if the scraper service or NSE/Screener is unreachable.

## Docs

- `CHANGELOG.md` — everything that changed in 0.2.0, and why.
- `docs/SCORING_FORMULA.md` — band-based scoring, category by category (§8: v2 changes).
- `docs/API_DESIGN.md` — every endpoint.
- `docs/ROADMAP.md` — what's done and what's next.
