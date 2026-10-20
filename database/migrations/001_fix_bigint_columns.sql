-- Run this ONCE against the stock_research database to fix a schema.sql
-- oversight: SERIAL creates an INTEGER column, but every JPA entity uses
-- Long ids, which Hibernate expects to map to BIGINT. This widens every
-- affected column in place -- it does NOT drop or reload your seed data,
-- and existing integer values fit into bigint with no data loss.

ALTER TABLE companies ALTER COLUMN id TYPE BIGINT;

ALTER TABLE financials ALTER COLUMN id TYPE BIGINT;
ALTER TABLE financials ALTER COLUMN company_id TYPE BIGINT;

ALTER TABLE ratios ALTER COLUMN id TYPE BIGINT;
ALTER TABLE ratios ALTER COLUMN company_id TYPE BIGINT;

ALTER TABLE shareholding_pattern ALTER COLUMN id TYPE BIGINT;
ALTER TABLE shareholding_pattern ALTER COLUMN company_id TYPE BIGINT;

ALTER TABLE prices ALTER COLUMN id TYPE BIGINT;
ALTER TABLE prices ALTER COLUMN company_id TYPE BIGINT;

ALTER TABLE news_items ALTER COLUMN id TYPE BIGINT;
ALTER TABLE news_items ALTER COLUMN company_id TYPE BIGINT;

ALTER TABLE scrape_metadata ALTER COLUMN id TYPE BIGINT;
ALTER TABLE scrape_metadata ALTER COLUMN company_id TYPE BIGINT;

ALTER TABLE analysis_reports ALTER COLUMN id TYPE BIGINT;
ALTER TABLE analysis_reports ALTER COLUMN company_id TYPE BIGINT;

ALTER TABLE report_evidence ALTER COLUMN id TYPE BIGINT;
ALTER TABLE report_evidence ALTER COLUMN report_id TYPE BIGINT;

ALTER TABLE scoring_rules ALTER COLUMN id TYPE BIGINT;
