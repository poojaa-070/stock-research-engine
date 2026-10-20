-- =========================================================
-- Stock Research Engine — Schema
-- Original tables reused as-is from the project brief.
-- Additions: scoring_rules (data-driven band config, see
-- docs/SCORING_FORMULA.md) and, in v2, watchlist plus a few indexes.
-- Upgrading a v1 database? Run migrations/002_v2_enhancements.sql
-- instead of re-creating it.
-- =========================================================

CREATE TABLE companies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    nse_symbol VARCHAR(50),
    bse_code VARCHAR(50),
    sector VARCHAR(100),
    industry VARCHAR(100),
    screener_url VARCHAR(500),
    business_description TEXT,  -- what they do / how they make money, from Screener's "About" section
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE financials (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id),
    period_type VARCHAR(20) NOT NULL, -- 'annual' | 'quarterly'
    period_end_date DATE NOT NULL,
    revenue NUMERIC,
    net_profit NUMERIC,
    operating_profit NUMERIC,
    eps NUMERIC,
    total_debt NUMERIC,
    free_cash_flow NUMERIC,
    operating_cash_flow NUMERIC,
    interest_expense NUMERIC,      -- enables real Interest Coverage Ratio
    current_assets NUMERIC,        -- enables Current Ratio
    current_liabilities NUMERIC,   -- enables Current Ratio
    UNIQUE (company_id, period_type, period_end_date)
);

CREATE TABLE ratios (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id),
    period_end_date DATE NOT NULL,
    roe NUMERIC,
    roce NUMERIC,
    debt_to_equity NUMERIC,
    pe_ratio NUMERIC,
    pb_ratio NUMERIC,
    ev_ebitda NUMERIC,
    peg_ratio NUMERIC,
    dividend_yield NUMERIC,
    UNIQUE (company_id, period_end_date)
);

CREATE TABLE shareholding_pattern (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id),
    period_end_date DATE NOT NULL,
    promoter_pct NUMERIC,
    fii_pct NUMERIC,
    dii_pct NUMERIC,
    public_pct NUMERIC,
    UNIQUE (company_id, period_end_date)
);

CREATE TABLE prices (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id),
    trade_date DATE NOT NULL,
    open NUMERIC,
    high NUMERIC,
    low NUMERIC,
    close NUMERIC,
    volume BIGINT,
    UNIQUE (company_id, trade_date)
);

CREATE TABLE news_items (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id),
    headline TEXT NOT NULL,
    url VARCHAR(500),
    source VARCHAR(100),
    published_at TIMESTAMP,
    sentiment VARCHAR(20) -- 'positive' | 'neutral' | 'negative'
);

CREATE TABLE scrape_metadata (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id),
    source VARCHAR(50) NOT NULL, -- 'screener' | 'nse'
    last_scraped_at TIMESTAMP,
    status VARCHAR(20), -- 'success' | 'failed' | 'stale'
    raw_payload JSONB
);

CREATE TABLE analysis_reports (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT REFERENCES companies(id),
    generated_at TIMESTAMP DEFAULT now(),
    overall_score NUMERIC,
    financial_health_score NUMERIC,
    business_quality_score NUMERIC,
    growth_score NUMERIC,
    valuation_score NUMERIC,
    management_score NUMERIC,
    risk_score NUMERIC,
    momentum_score NUMERIC,
    verdict VARCHAR(50),
    confidence NUMERIC
);

CREATE TABLE report_evidence (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT REFERENCES analysis_reports(id),
    category VARCHAR(50),
    sentiment VARCHAR(20),
    statement TEXT NOT NULL,
    source_ref VARCHAR(255)
);

-- =========================================================
-- NEW: scoring_rules — data-driven band config so the
-- deterministic formula can be tuned without a redeploy.
-- See docs/SCORING_FORMULA.md section 1 for how bands work.
-- =========================================================
CREATE TABLE scoring_rules (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,       -- 'financial_health' | 'business_quality' | 'growth' | 'valuation' | 'management' | 'risk' | 'momentum'
    metric_key VARCHAR(50) NOT NULL,     -- 'roce', 'debt_to_equity', 'pe_vs_history', ...
    band_thresholds NUMERIC[] NOT NULL,  -- n thresholds, ascending
    band_scores NUMERIC[] NOT NULL,      -- n+1 scores (below-first .. above-last)
    weight_within_category NUMERIC NOT NULL,
    higher_is_better BOOLEAN DEFAULT true,
    active BOOLEAN DEFAULT true,
    updated_at TIMESTAMP DEFAULT now(),
    sector VARCHAR(100)                  -- NULL = default rule; set = override for that sector only (v2)
);
-- One rule per (category, metric, sector), where NULL sector is the default.
CREATE UNIQUE INDEX ux_scoring_rules_cat_metric_sector
    ON scoring_rules (category, metric_key, COALESCE(sector, ''));

-- =========================================================
-- v2: watchlist — single-user list of companies being tracked.
-- =========================================================
CREATE TABLE watchlist (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    note TEXT,
    added_at TIMESTAMP DEFAULT now(),
    UNIQUE (company_id)
);

CREATE INDEX idx_financials_company_period ON financials(company_id, period_end_date);
CREATE INDEX idx_ratios_company_period ON ratios(company_id, period_end_date);
CREATE INDEX idx_reports_company ON analysis_reports(company_id, generated_at DESC);
CREATE INDEX idx_evidence_report_category ON report_evidence(report_id, category);
CREATE INDEX idx_news_company_published ON news_items (company_id, published_at DESC);
CREATE INDEX idx_prices_company_date ON prices (company_id, trade_date DESC);
CREATE INDEX idx_shareholding_company_period ON shareholding_pattern (company_id, period_end_date);
CREATE INDEX idx_scrape_metadata_company_source ON scrape_metadata (company_id, source, last_scraped_at DESC);
-- v2: the scraper re-inserted every NSE announcement on each refresh.
CREATE UNIQUE INDEX ux_news_company_headline_published
    ON news_items (company_id, headline, COALESCE(published_at, 'epoch'::timestamp));
