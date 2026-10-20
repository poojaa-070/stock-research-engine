-- Seed data for scoring_rules — mirrors docs/SCORING_FORMULA.md section 2.
-- Array convention: band_thresholds has N ascending cut points;
-- band_scores has N+1 scores: [below t1, between t1-t2, ..., above tN].
--
-- Rebalanced to cover the full professional-investor checklist: real
-- Interest Coverage (needs interest_expense, now in schema), Current Ratio
-- (needs current_assets/current_liabilities, now in schema), Net Debt/EBITDA,
-- an FCF *growth rate* (not just direction), and a P/B check for
-- asset-heavy/financial sectors. Qualitative flags are now THREE separate
-- pools (business_quality, management, risk) instead of one shared pool
-- reused across categories, so a single flag no longer gets applied twice
-- against unrelated categories — see FlagDeductionRules.java.

-- Financial Health (category weight 20% handled in application config)
INSERT INTO scoring_rules (category, metric_key, band_thresholds, band_scores, weight_within_category, higher_is_better) VALUES
('financial_health', 'debt_to_equity',      '{0.5,1,2,3}',        '{100,85,60,35,10}',      0.20, false),
('financial_health', 'interest_coverage',   '{1,2,4,8,15}',       '{5,30,45,60,85,100}',    0.20, true),
('financial_health', 'current_ratio',       '{0.8,1,1.2,1.5,2}',  '{10,40,60,80,100,100}',  0.15, true),
('financial_health', 'net_debt_to_ebitda',  '{0.5,1,2,3,4}',      '{100,90,70,45,20,10}',   0.15, false),
('financial_health', 'cash_conversion',     '{0.5,0.8,1,1.2,1.5}','{10,40,55,70,90,100}',   0.15, true),
('financial_health', 'fcf_cagr_3y',         '{-10,0,10,20,30}',   '{10,35,60,80,95,100}',   0.15, true);

-- Business Quality — moat depth + industry attractiveness now feed the
-- qualitative pool alongside the original moat_erosion/market_share flags.
INSERT INTO scoring_rules (category, metric_key, band_thresholds, band_scores, weight_within_category, higher_is_better) VALUES
('business_quality', 'roce_latest',          '{5,10,15,20,25}', '{15,40,65,85,100,100}', 0.30, true),
('business_quality', 'roce_trend',           '{-1,0,1}',        '{20,50,85,85}', 0.15, true),
('business_quality', 'margin_stability',     '{0.03,0.08,0.15}','{90,90,60,30}', 0.15, false), -- thresholds must ascend (v1 had them reversed)
('business_quality', 'qualitative_flags',    '{}',              '{60}' , 0.40, true); -- moat type, market share, industry structure — see FlagDeductionRules

-- Growth
INSERT INTO scoring_rules (category, metric_key, band_thresholds, band_scores, weight_within_category, higher_is_better) VALUES
('growth', 'revenue_cagr_3y', '{0,5,10,15,20}', '{20,45,65,85,100,100}', 0.30, true),
('growth', 'profit_cagr_3y',  '{0,5,10,15,20}', '{15,40,65,85,100,100}', 0.30, true),
('growth', 'eps_cagr_3y',     '{0,5,10,15,20}', '{15,40,65,85,100,100}', 0.25, true),
('growth', 'fcf_cagr_3y',     '{-10,0,10,20,30}','{10,35,60,80,95,100}', 0.15, true);

-- Valuation — now includes a P/B check, important for banks/asset-heavy
-- sectors where P/E alone is a poor signal.
INSERT INTO scoring_rules (category, metric_key, band_thresholds, band_scores, weight_within_category, higher_is_better) VALUES
('valuation', 'pe_vs_own_history', '{0.6,0.8,1,1.2,1.5}', '{100,90,70,45,20,20}', 0.25, false),
('valuation', 'pe_vs_peer_median', '{0.6,0.8,1,1.2,1.5}', '{100,90,70,45,20,20}', 0.20, false),
('valuation', 'peg_ratio',         '{0.5,0.7,1,1.5,2}',   '{100,90,70,45,20,20}', 0.20, false),
('valuation', 'ev_ebitda_vs_peer', '{0.6,0.8,1,1.2,1.5}', '{100,90,70,45,20,20}', 0.20, false),
('valuation', 'pb_ratio',          '{1,2,3,5}',           '{90,70,50,25,10}',     0.15, false);

-- Management — governance/capital-allocation qualitative flags (credibility,
-- related-party transactions, auditor qualifications) now have their own
-- pool here instead of being lumped into Business Quality.
INSERT INTO scoring_rules (category, metric_key, band_thresholds, band_scores, weight_within_category, higher_is_better) VALUES
('management', 'promoter_holding_delta_3y', '{-5,0,0.01,5}',    '{15,40,70,90,100}',    0.20, true),
('management', 'promoter_holding_latest',   '{10,25,40,60,75}', '{30,55,70,85,95,100}', 0.20, true),
('management', 'pledged_share_pct',         '{0,10,25,50}',     '{100,65,35,10,10}',    0.15, false),
('management', 'dividend_years_paid_of_5',  '{0,2,4,5}',        '{30,50,75,100,100}',   0.20, true),
('management', 'qualitative_flags',         '{}',               '{60}' ,                0.25, true); -- governance/capital-allocation flags

-- Risk (this category scores "safety" — higher = safer). Consolidated to
-- ONE qualitative pool (was two, which double-applied every flag).
INSERT INTO scoring_rules (category, metric_key, band_thresholds, band_scores, weight_within_category, higher_is_better) VALUES
('risk', 'debt_to_equity',        '{0.5,1,2,3}',   '{100,85,60,35,10}', 0.30, false),
('risk', 'customer_concentration','{10,15,30,50}', '{100,80,50,20,20}', 0.25, false),
('risk', 'qualitative_flags',     '{}',            '{60}' ,             0.45, true); -- regulatory/commodity/competitive-structure flags

-- Momentum (optional layer)
INSERT INTO scoring_rules (category, metric_key, band_thresholds, band_scores, weight_within_category, higher_is_better) VALUES
('momentum', 'price_vs_200dma_pct', '{-15,-5,0,5,15}', '{10,40,60,80,100,100}', 0.40, true),
('momentum', 'ma_crossover_signal', '{-1,0,1}',        '{20,50,90,90}', 0.30, true),
('momentum', 'rsi_14',              '{30,50,70}',      '{70,55,65,30}', 0.30, false); -- U-shaped, handled specially in code

-- v2: the qualitative_flags rows above store the NEUTRAL BASELINE (60) the
-- flag pool starts from in band_scores. Negative flags deduct from it,
-- positive flags add to it, capped to [0, 100]. (v1 started at 100, so
-- "no flags found" scored as perfect and positive flags had no effect.)

-- v2: sector-specific overrides (ROADMAP Phase 3). A row with a sector
-- replaces the default row for the same category+metric_key, for
-- companies in that sector only. Illustrative starting points.
INSERT INTO scoring_rules (category, metric_key, band_thresholds, band_scores, weight_within_category, higher_is_better, sector) VALUES
('valuation',        'pb_ratio',    '{1,2,3,4.5}',      '{95,85,65,40,20}',      0.25, false, 'Financial Services'),
('business_quality', 'roce_latest', '{10,20,30,40,50}', '{15,40,60,80,95,100}',  0.30, true,  'IT');
