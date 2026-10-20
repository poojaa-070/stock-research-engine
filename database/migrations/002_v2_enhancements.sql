-- =========================================================
-- Migration 002 — v2 enhancements. Safe to run on an existing v1
-- database (every statement is idempotent) and a no-op on a fresh
-- database created from the updated schema.sql.
--
--   psql stock_research -f database/migrations/002_v2_enhancements.sql
--
-- Changes:
--  1. scoring_rules.sector — optional sector-specific band overrides
--     (ROADMAP Phase 3). NULL = the default rule for every sector.
--  2. watchlist table — single-user watchlist (ROADMAP Phase 6, first step).
--  3. news_items de-duplication — the scraper inserted every NSE
--     announcement again on every refresh. Existing duplicates are
--     removed, then a unique index stops new ones.
--  4. margin_stability band fix — v1 seeded its thresholds in DESCENDING
--     order, but BandScorer requires ascending thresholds, so the most
--     stable companies were scored worst. Re-seeded correctly here.
--  5. qualitative_flags rows — band_scores now holds the neutral baseline
--     (60) the flag pool starts from, instead of 100 (which meant "no
--     flags found" scored as perfect and positive flags could never add).
-- =========================================================

-- 1. Sector-specific scoring rules -------------------------------------
ALTER TABLE scoring_rules ADD COLUMN IF NOT EXISTS sector VARCHAR(100);

ALTER TABLE scoring_rules DROP CONSTRAINT IF EXISTS scoring_rules_category_metric_key_key;
CREATE UNIQUE INDEX IF NOT EXISTS ux_scoring_rules_cat_metric_sector
    ON scoring_rules (category, metric_key, COALESCE(sector, ''));

-- 2. Watchlist ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS watchlist (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    note TEXT,
    added_at TIMESTAMP DEFAULT now(),
    UNIQUE (company_id)
);

-- 3. News de-duplication -----------------------------------------------
DELETE FROM news_items a
USING news_items b
WHERE a.id > b.id
  AND a.company_id = b.company_id
  AND a.headline = b.headline
  AND a.published_at IS NOT DISTINCT FROM b.published_at;

CREATE UNIQUE INDEX IF NOT EXISTS ux_news_company_headline_published
    ON news_items (company_id, headline, COALESCE(published_at, 'epoch'::timestamp));

CREATE INDEX IF NOT EXISTS idx_news_company_published ON news_items (company_id, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_prices_company_date ON prices (company_id, trade_date DESC);
CREATE INDEX IF NOT EXISTS idx_shareholding_company_period ON shareholding_pattern (company_id, period_end_date);
CREATE INDEX IF NOT EXISTS idx_scrape_metadata_company_source ON scrape_metadata (company_id, source, last_scraped_at DESC);

-- 4. margin_stability: ascending thresholds ----------------------------
UPDATE scoring_rules
   SET band_thresholds = '{0.03,0.08,0.15}',
       band_scores     = '{90,90,60,30}',
       updated_at      = now()
 WHERE metric_key = 'margin_stability' AND sector IS NULL;

-- 5. Neutral qualitative baseline --------------------------------------
UPDATE scoring_rules
   SET band_scores = '{60}', updated_at = now()
 WHERE metric_key = 'qualitative_flags';

-- 6. Sector overrides (illustrative starting points — tune to taste) ---
-- Banks/NBFCs: P/B is the primary multiple and trades structurally higher
-- for quality lenders, so the generic P/B band (which penalises anything
-- above 3x) is too harsh. Leverage metrics are skipped for financials in
-- code (see MetricCalculator.isFinancialInstitution), so no D/E override.
INSERT INTO scoring_rules (category, metric_key, band_thresholds, band_scores, weight_within_category, higher_is_better, sector)
VALUES ('valuation', 'pb_ratio', '{1,2,3,4.5}', '{95,85,65,40,20}', 0.25, false, 'Financial Services')
ON CONFLICT DO NOTHING;

-- IT services: asset-light, so ROCE runs structurally high; a 25% ROCE is
-- merely average for the sector rather than exceptional.
INSERT INTO scoring_rules (category, metric_key, band_thresholds, band_scores, weight_within_category, higher_is_better, sector)
VALUES ('business_quality', 'roce_latest', '{10,20,30,40,50}', '{15,40,60,80,95,100}', 0.30, true, 'IT')
ON CONFLICT DO NOTHING;
