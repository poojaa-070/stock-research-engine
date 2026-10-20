-- Demo data for 3 companies so the API/frontend can be exercised end-to-end
-- without a live scrape. Numbers are illustrative, not real quotes — swap
-- with actual scraper output once screener/NSE scraping is wired up.

INSERT INTO companies (name, nse_symbol, bse_code, sector, industry, screener_url, business_description) VALUES
('HDFC Bank Ltd', 'HDFCBANK', '500180', 'Financial Services', 'Private Bank', 'https://www.screener.in/company/HDFCBANK/',
 'HDFC Bank is India''s largest private-sector bank by assets, offering retail and wholesale banking, treasury operations, and a range of financial products including loans, credit cards, and wealth management. It earns money primarily through net interest income and fee income from transaction banking, cards, and third-party product distribution.'),
('ICICI Bank Ltd', 'ICICIBANK', '532174', 'Financial Services', 'Private Bank', 'https://www.screener.in/company/ICICIBANK/',
 'ICICI Bank is one of India''s largest private-sector banks, providing retail banking, corporate banking, treasury, and insurance/asset-management services through subsidiaries. It makes money through net interest income and a growing share of fee-based income from digital banking, cards, and third-party distribution.'),
('Tata Consultancy Services Ltd', 'TCS', '532540', 'IT', 'IT Services', 'https://www.screener.in/company/TCS/',
 'Tata Consultancy Services is India''s largest IT services company, providing consulting, technology, and outsourcing services to clients across banking, retail, manufacturing, and other industries worldwide. It makes money primarily through long-term services contracts, with a growing share from digital transformation, cloud, and AI-related engagements.');

-- Annual financials, last 2 FYs, per company (id 1=HDFCBANK, 2=ICICIBANK, 3=TCS)
INSERT INTO financials (company_id, period_type, period_end_date, revenue, net_profit, operating_profit, eps, total_debt, free_cash_flow, operating_cash_flow, interest_expense, current_assets, current_liabilities) VALUES
(1,'annual','2025-03-31', 210000, 65000, 90000, 85.2, 950000, 12000, 45000, NULL, NULL, NULL),
(1,'annual','2026-03-31', 235000, 72000, 99000, 94.1, 1010000, 15000, 51000, NULL, NULL, NULL),
(2,'annual','2025-03-31', 175000, 45000, 68000, 65.4, 720000, 9000, 34000, NULL, NULL, NULL),
(2,'annual','2026-03-31', 192000, 51000, 75000, 73.0, 760000, 11000, 39000, NULL, NULL, NULL),
(3,'annual','2025-03-31', 245000, 45000, 60000, 124.5, 5000, 39000, 42000, 150, 128000, 54000),
(3,'annual','2026-03-31', 262000, 48500, 63500, 133.8, 4500, 42000, 45000, 140, 136000, 57000);

-- Note: interest_expense and current_assets/current_liabilities are
-- deliberately NULL for HDFC and ICICI (companies 1 and 2). Banks don't
-- have a meaningful "current ratio" or a standalone "interest expense" the
-- way non-financial companies do -- interest paid to depositors IS the core
-- operating cost of a bank, not a separate financing expense, and a bank's
-- balance sheet doesn't separate "current" vs "non-current" assets the way
-- a manufacturing or IT services company's does. Scoring these as if they
-- were a normal company would produce a misleading number, not a useful
-- one -- consistent with this project's "absent, not faked" principle.

-- Quarterly financials (2 quarters per company: latest + same quarter a
-- year earlier, so the Latest Quarter YoY feature has something to compare
-- against). Illustrative, not real reported figures.
INSERT INTO financials (company_id, period_type, period_end_date, revenue, net_profit, operating_profit) VALUES
(1,'quarterly','2025-06-30', 56430, 16640, 22800),
(1,'quarterly','2026-06-30', 62500, 18200, 25100),
(2,'quarterly','2025-06-30', 46930, 11614, 17200),
(2,'quarterly','2026-06-30', 51200, 13100, 19300),
(3,'quarterly','2025-06-30', 62900, 11910, 15400),
(3,'quarterly','2026-06-30', 66800, 12600, 16400);

INSERT INTO ratios (company_id, period_end_date, roe, roce, debt_to_equity, pe_ratio, pb_ratio, ev_ebitda, peg_ratio, dividend_yield) VALUES
(1,'2026-03-31', 16.8, 14.2, 6.5, 19.5, 2.9, 14.1, 1.3, 1.1),
(2,'2026-03-31', 17.5, 15.1, 5.9, 18.2, 2.7, 13.4, 1.1, 0.9),
(3,'2026-03-31', 43.2, 58.6, 0.02, 27.8, 12.1, 19.5, 2.6, 1.6);

INSERT INTO shareholding_pattern (company_id, period_end_date, promoter_pct, fii_pct, dii_pct, public_pct) VALUES
(1,'2025-06-30', 0, 47.2, 33.1, 19.7),
(1,'2026-06-30', 0, 48.9, 32.0, 19.1),
(2,'2025-06-30', 0, 45.6, 35.4, 19.0),
(2,'2026-06-30', 0, 44.1, 36.9, 19.0),
(3,'2025-06-30', 71.8, 12.4, 9.1, 6.7),
(3,'2026-06-30', 71.8, 12.0, 9.6, 6.6);

-- (0% promoter holding for the two banks is realistic — they're professionally
-- held, not promoter-owned; the scoring engine should treat "not applicable"
-- differently from "falling", see BusinessQualityScorer notes.)

INSERT INTO news_items (company_id, headline, url, source, published_at, sentiment) VALUES
(1,'HDFC Bank reports steady deposit growth in Q1', 'https://example.com/n1','Moneycontrol','2026-07-15','positive'),
(2,'ICICI Bank flags rising provisioning for unsecured retail loans', 'https://example.com/n2','Economic Times','2026-07-20','negative'),
(3,'TCS signs large multi-year deal with European retailer', 'https://example.com/n3','Livemint','2026-08-02','positive');

INSERT INTO scrape_metadata (company_id, source, last_scraped_at, status, raw_payload) VALUES
(1,'screener','2026-09-01 06:00:00','success','{"note":"demo seed, not a live scrape"}'),
(2,'screener','2026-09-01 06:00:00','success','{"note":"demo seed, not a live scrape"}'),
(3,'screener','2026-09-01 06:00:00','success','{"note":"demo seed, not a live scrape"}');
