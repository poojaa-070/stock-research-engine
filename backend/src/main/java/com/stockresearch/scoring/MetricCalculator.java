package com.stockresearch.scoring;

import com.stockresearch.dto.MetricInputs;
import com.stockresearch.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure, deterministic metric computation. No AI, no network calls — just
 * arithmetic over what's already in Postgres. Every metric that genuinely
 * can't be computed from the current schema is a documented gap below
 * rather than a guess; the scoring engine renormalizes category weights
 * around any metric that MetricInputs doesn't contain.
 *
 * v2 corrections (see CHANGELOG.md):
 *  - CAGR of a figure that turned negative used to produce NaN, which the
 *    BandScorer then scored as 100. It is now scored as a collapse (-100%).
 *  - "*_cagr_3y" metrics used the whole available history (often 5+ years);
 *    they now use a genuine ~3-year window when the data allows it.
 *  - Leverage metrics (D/E, debt/EBITDA) are skipped for banks/NBFCs/insurers,
 *    for the same reason interest coverage and current ratio already were:
 *    borrowing IS a lender's raw material, so a "high" D/E is structural.
 *  - RSI uses Wilder's smoothing; ROCE trend ignores sub-0.5pp noise.
 */
@Component
public class MetricCalculator {

    /** Window, in years, for the *_cagr_3y metrics. */
    static final int CAGR_WINDOW_YEARS = 3;
    /** ROCE moves smaller than this (percentage points) count as flat. */
    static final double ROCE_TREND_DEADBAND_PP = 0.5;

    private final boolean skipLeverageForFinancials;

    public MetricCalculator(@Value("${scoring.skip-leverage-for-financials:true}") boolean skipLeverageForFinancials) {
        this.skipLeverageForFinancials = skipLeverageForFinancials;
    }

    public MetricInputs compute(
            Company company,
            List<Financials> annualFinancials,          // ascending by period_end_date
            List<Ratios> ratiosHistory,                  // ascending by period_end_date
            List<ShareholdingPattern> shareholdingHistory,// ascending by period_end_date
            List<Prices> priceHistoryDesc,                // descending by trade_date (most recent first)
            List<Ratios> sectorPeerLatestRatios           // latest Ratios row per peer company, same sector
    ) {
        MetricInputs inputs = new MetricInputs();
        boolean skipLeverage = skipLeverageForFinancials && isFinancialInstitution(company);

        computeFinancialHealth(inputs, annualFinancials, ratiosHistory, skipLeverage);
        computeBusinessQuality(inputs, annualFinancials, ratiosHistory);
        computeGrowth(inputs, annualFinancials);
        computeValuation(inputs, ratiosHistory, sectorPeerLatestRatios);
        computeManagement(inputs, shareholdingHistory, ratiosHistory);
        computeRisk(inputs, ratiosHistory, skipLeverage);
        computeMomentum(inputs, priceHistoryDesc);

        return inputs;
    }

    /**
     * Banks, NBFCs, insurers and similar lenders. Matched on sector/industry
     * text because that's what Screener exposes; deliberately conservative
     * (an unknown sector is treated as non-financial, i.e. scored normally).
     */
    public static boolean isFinancialInstitution(Company company) {
        if (company == null) return false;
        String text = ((company.getSector() == null ? "" : company.getSector()) + " "
                + (company.getIndustry() == null ? "" : company.getIndustry())).toLowerCase(Locale.ROOT);
        return text.contains("bank") || text.contains("nbfc") || text.contains("financial services")
                || text.contains("finance") || text.contains("insurance") || text.contains("lending");
    }

    // ---------------------------------------------------------------
    // Financial Health
    // ---------------------------------------------------------------
    private void computeFinancialHealth(MetricInputs inputs, List<Financials> fin, List<Ratios> ratios,
                                        boolean skipLeverage) {
        if (!skipLeverage) {
            latest(ratios, Ratios::getDebtToEquity).ifPresent(v ->
                    inputs.put("debt_to_equity", v.doubleValue(),
                            "Debt-to-equity is " + v, "ratios table, latest period"));
        }

        if (!fin.isEmpty()) {
            Financials last = fin.get(fin.size() - 1);

            if (last.getOperatingCashFlow() != null && last.getNetProfit() != null
                    && last.getNetProfit().doubleValue() > 0) {
                // Only meaningful against a positive profit: OCF / a loss flips sign
                // and would read as "great conversion" for a loss-making company.
                double conversion = last.getOperatingCashFlow().doubleValue() / last.getNetProfit().doubleValue();
                inputs.put("cash_conversion", conversion,
                        "Operating cash flow / net profit = " + round(conversion),
                        "financials table, FY" + last.getPeriodEndDate());
            }

            // Interest Coverage Ratio = Operating Profit / Interest Expense.
            // Tells you if the company can comfortably service debt during a
            // downturn. For banks/NBFCs, interest paid to depositors IS the core
            // operating cost, so the scraper leaves interest_expense null there.
            if (last.getOperatingProfit() != null && last.getInterestExpense() != null
                    && last.getInterestExpense().doubleValue() > 0) {
                double coverage = last.getOperatingProfit().doubleValue() / last.getInterestExpense().doubleValue();
                inputs.put("interest_coverage", coverage,
                        "Interest coverage = operating profit / interest expense = " + round(coverage) + "x",
                        "financials table, FY" + last.getPeriodEndDate());
            }

            // Current Ratio = Current Assets / Current Liabilities.
            if (last.getCurrentAssets() != null && last.getCurrentLiabilities() != null
                    && last.getCurrentLiabilities().doubleValue() > 0) {
                double currentRatio = last.getCurrentAssets().doubleValue() / last.getCurrentLiabilities().doubleValue();
                inputs.put("current_ratio", currentRatio,
                        "Current ratio = current assets / current liabilities = " + round(currentRatio),
                        "financials table, FY" + last.getPeriodEndDate());
            }

            // Debt / EBITDA — years of core earnings needed to repay all debt.
            // operating_profit is an EBITDA proxy (Screener's "Operating Profit"
            // may already net out D&A depending on how a company reports).
            if (!skipLeverage && last.getTotalDebt() != null && last.getOperatingProfit() != null
                    && last.getOperatingProfit().doubleValue() > 0) {
                double debtToEbitda = last.getTotalDebt().doubleValue() / last.getOperatingProfit().doubleValue();
                inputs.put("net_debt_to_ebitda", debtToEbitda,
                        "Total debt / operating profit (EBITDA proxy) = " + round(debtToEbitda) + "x",
                        "financials table, FY" + last.getPeriodEndDate());
            }
        }

        cagrWindow(fin).ifPresent(w ->
                cagr(w.first().getFreeCashFlow(), w.last().getFreeCashFlow(), w.years()).ifPresent(v ->
                        inputs.put("fcf_cagr_3y", v,
                                "FCF CAGR over " + fmtYears(w.years()) + ": " + describeCagr(v)
                                        + " (from " + w.first().getFreeCashFlow() + " to " + w.last().getFreeCashFlow() + ")",
                                w.sourceRef())));
    }

    // ---------------------------------------------------------------
    // Business Quality
    // ---------------------------------------------------------------
    private void computeBusinessQuality(MetricInputs inputs, List<Financials> fin, List<Ratios> ratios) {
        latest(ratios, Ratios::getRoce).ifPresent(v ->
                inputs.put("roce_latest", v.doubleValue(),
                        "Latest ROCE is " + v + "%", "ratios table, latest period"));

        List<Ratios> withRoce = ratios.stream().filter(r -> r.getRoce() != null).toList();
        if (withRoce.size() >= 2) {
            double first = withRoce.get(0).getRoce().doubleValue();
            double last = withRoce.get(withRoce.size() - 1).getRoce().doubleValue();
            double move = last - first;
            double direction = Math.abs(move) < ROCE_TREND_DEADBAND_PP ? 0 : Math.signum(move);
            inputs.put("roce_trend", direction,
                    "ROCE moved from " + first + "% to " + last + "% across the available history ("
                            + (direction > 0 ? "improving" : direction < 0 ? "declining" : "broadly flat") + ")",
                    "ratios table, multi-period");
        }

        if (fin.size() >= 2) {
            List<Double> margins = fin.stream()
                    .filter(f -> f.getRevenue() != null && f.getOperatingProfit() != null && f.getRevenue().doubleValue() != 0)
                    .map(f -> f.getOperatingProfit().doubleValue() / f.getRevenue().doubleValue())
                    .toList();
            if (margins.size() >= 2) {
                double stdev = stdDev(margins);
                inputs.put("margin_stability", stdev,
                        "Operating margin std-dev across " + margins.size() + " periods = " + round(stdev),
                        "financials table, multi-period");
            }
        }
        // qualitative_flags is resolved via FlagDeductionRules in the
        // ScoringEngine, not here — it needs the AI-extracted flag list.
    }

    // ---------------------------------------------------------------
    // Growth
    // ---------------------------------------------------------------
    private void computeGrowth(MetricInputs inputs, List<Financials> fin) {
        cagrWindow(fin).ifPresent(w -> {
            cagr(w.first().getRevenue(), w.last().getRevenue(), w.years()).ifPresent(v ->
                    inputs.put("revenue_cagr_3y", v,
                            "Revenue CAGR over " + fmtYears(w.years()) + ": " + describeCagr(v), w.sourceRef()));
            cagr(w.first().getNetProfit(), w.last().getNetProfit(), w.years()).ifPresent(v ->
                    inputs.put("profit_cagr_3y", v,
                            "Net profit CAGR over " + fmtYears(w.years()) + ": " + describeCagr(v), w.sourceRef()));
            cagr(w.first().getEps(), w.last().getEps(), w.years()).ifPresent(v ->
                    inputs.put("eps_cagr_3y", v,
                            "EPS CAGR over " + fmtYears(w.years()) + ": " + describeCagr(v), w.sourceRef()));
        });
    }

    // ---------------------------------------------------------------
    // Valuation — relative to own history and to sector peers
    // ---------------------------------------------------------------
    private void computeValuation(MetricInputs inputs, List<Ratios> ratios, List<Ratios> peerLatest) {
        if (ratios.isEmpty()) return;
        Ratios latest = ratios.get(ratios.size() - 1);

        List<Double> ownPe = ratios.stream().map(Ratios::getPeRatio).filter(Objects::nonNull)
                .map(BigDecimal::doubleValue).filter(v -> v > 0).toList();
        if (ownPe.size() >= 2 && latest.getPeRatio() != null && latest.getPeRatio().doubleValue() > 0) {
            double historicalMedianPe = median(ownPe);
            double ratio = latest.getPeRatio().doubleValue() / historicalMedianPe;
            inputs.put("pe_vs_own_history", ratio,
                    "Current PE " + latest.getPeRatio() + " vs own " + ownPe.size() + "-period median " + round(historicalMedianPe),
                    "ratios table, own history");
        }

        if (!peerLatest.isEmpty() && latest.getPeRatio() != null && latest.getPeRatio().doubleValue() > 0) {
            double peerMedianPe = median(peerLatest.stream()
                    .map(Ratios::getPeRatio).filter(Objects::nonNull).map(BigDecimal::doubleValue)
                    .filter(v -> v > 0).toList());
            if (peerMedianPe > 0) {
                double ratio = latest.getPeRatio().doubleValue() / peerMedianPe;
                inputs.put("pe_vs_peer_median", ratio,
                        "Current PE " + latest.getPeRatio() + " vs sector peer median PE " + round(peerMedianPe),
                        "ratios table, sector peers");
            }
            double peerMedianEvEbitda = median(peerLatest.stream()
                    .map(Ratios::getEvEbitda).filter(Objects::nonNull).map(BigDecimal::doubleValue)
                    .filter(v -> v > 0).toList());
            if (peerMedianEvEbitda > 0 && latest.getEvEbitda() != null && latest.getEvEbitda().doubleValue() > 0) {
                double ratio = latest.getEvEbitda().doubleValue() / peerMedianEvEbitda;
                inputs.put("ev_ebitda_vs_peer", ratio,
                        "Current EV/EBITDA " + latest.getEvEbitda() + " vs sector peer median " + round(peerMedianEvEbitda),
                        "ratios table, sector peers");
            }
        }

        if (latest.getPegRatio() != null && latest.getPegRatio().doubleValue() > 0) {
            inputs.put("peg_ratio", latest.getPegRatio().doubleValue(),
                    "PEG ratio is " + latest.getPegRatio(), "ratios table, latest period");
        }

        // P/B — especially important for banks and asset-heavy industries
        // where P/E alone can be a misleading valuation signal.
        if (latest.getPbRatio() != null && latest.getPbRatio().doubleValue() > 0) {
            inputs.put("pb_ratio", latest.getPbRatio().doubleValue(),
                    "P/B ratio is " + latest.getPbRatio(), "ratios table, latest period");
        }
    }

    // ---------------------------------------------------------------
    // Management
    // ---------------------------------------------------------------
    private void computeManagement(MetricInputs inputs, List<ShareholdingPattern> holdings, List<Ratios> ratios) {
        List<ShareholdingPattern> withPromoter = holdings.stream()
                .filter(h -> h.getPromoterPct() != null)
                .sorted(Comparator.comparing(ShareholdingPattern::getPeriodEndDate))
                .toList();

        if (!withPromoter.isEmpty()) {
            double latest = withPromoter.get(withPromoter.size() - 1).getPromoterPct().doubleValue();
            if (latest > 0) {
                // Absolute stake, independent of trend. Skipped (not scored as
                // low) for professionally-held companies like private banks,
                // where 0% is structural rather than a governance signal.
                inputs.put("promoter_holding_latest", latest,
                        "Promoter holding stands at " + latest + "% of equity",
                        "shareholding_pattern table, latest period");
            }
        }

        if (withPromoter.size() >= 2) {
            ShareholdingPattern firstRow = withPromoter.get(0);
            ShareholdingPattern lastRow = withPromoter.get(withPromoter.size() - 1);
            double first = firstRow.getPromoterPct().doubleValue();
            double last = lastRow.getPromoterPct().doubleValue();
            if (!(first == 0 && last == 0)) {
                inputs.put("promoter_holding_delta_3y", last - first,
                        "Promoter holding moved from " + first + "% to " + last + "% ("
                                + firstRow.getPeriodEndDate() + " to " + lastRow.getPeriodEndDate() + ")",
                        "shareholding_pattern table");
            }
            // else: professionally-held company — not applicable, left absent.
        }

        // pledged_share_pct: NOT in current schema — left absent.

        // dividend_years_paid_of_5: proxy until a per-year dividend table exists
        // (count of ratio snapshots with a positive dividend yield).
        List<Ratios> withYield = ratios.stream().filter(r -> r.getDividendYield() != null).toList();
        if (!withYield.isEmpty()) {
            long paidPeriods = withYield.stream().filter(r -> r.getDividendYield().doubleValue() > 0).count();
            double proxyOutOf5 = Math.min(5, (paidPeriods / (double) withYield.size()) * 5);
            inputs.put("dividend_years_paid_of_5", proxyOutOf5,
                    "Proxy: " + paidPeriods + " of " + withYield.size() + " snapshots show a positive dividend yield",
                    "ratios table (proxy — see MetricCalculator notes)");
        }
    }

    // ---------------------------------------------------------------
    // Risk
    // ---------------------------------------------------------------
    private void computeRisk(MetricInputs inputs, List<Ratios> ratios, boolean skipLeverage) {
        if (!skipLeverage) {
            latest(ratios, Ratios::getDebtToEquity).ifPresent(v ->
                    inputs.put("debt_to_equity", v.doubleValue(), // shared metric_key with financial_health
                            "Debt-to-equity is " + v, "ratios table, latest period"));
        }
        // customer_concentration: not in schema; Screener doesn't expose it.
        // Qualitative risk flags are resolved via FlagDeductionRules.
    }

    // ---------------------------------------------------------------
    // Momentum
    // ---------------------------------------------------------------
    private void computeMomentum(MetricInputs inputs, List<Prices> pricesDesc) {
        List<Double> closesAsc = closesAscending(pricesDesc);
        if (closesAsc.size() < 200) return; // not enough history for a 200-DMA yet

        double currentClose = closesAsc.get(closesAsc.size() - 1);
        double dma200 = Technicals.sma(closesAsc, 200).orElse(currentClose);
        double pctVs200 = ((currentClose - dma200) / dma200) * 100;
        inputs.put("price_vs_200dma_pct", pctVs200,
                "Price is " + round(pctVs200) + "% vs its 200-day moving average",
                "prices table, trailing 200 sessions");

        double dma50 = Technicals.sma(closesAsc, 50).orElse(currentClose);
        double signal = Math.signum(dma50 - dma200);
        inputs.put("ma_crossover_signal", signal,
                signal > 0 ? "50-DMA is above 200-DMA (golden-cross side)" : "50-DMA is below 200-DMA (death-cross side)",
                "prices table, trailing 200 sessions");

        Technicals.rsiWilder(closesAsc, 14).ifPresent(rsi ->
                inputs.put("rsi_14", rsi, "RSI(14, Wilder) = " + round(rsi),
                        "prices table, " + closesAsc.size() + " sessions"));
    }

    // ---------------------------------------------------------------
    // Helpers (package-visible where tests need them)
    // ---------------------------------------------------------------

    /** First/last annual rows bracketing the CAGR window, plus the exact span in years. */
    record CagrWindow(Financials first, Financials last, double years) {
        String sourceRef() {
            return "financials table, FY" + first.getPeriodEndDate() + " to FY" + last.getPeriodEndDate();
        }
    }

    /**
     * Picks the annual row closest to {@link #CAGR_WINDOW_YEARS} before the
     * latest one. If history is shorter than that, uses the earliest row and
     * the evidence note states the real (shorter) span — never mislabelled.
     */
    static Optional<CagrWindow> cagrWindow(List<Financials> fin) {
        if (fin.size() < 2) return Optional.empty();
        Financials last = fin.get(fin.size() - 1);
        LocalDate target = last.getPeriodEndDate().minusYears(CAGR_WINDOW_YEARS);
        Financials first = fin.get(0);
        for (Financials f : fin) {
            if (f == last) continue;
            if (!f.getPeriodEndDate().isBefore(target.minusDays(120))
                    && Math.abs(ChronoUnit.DAYS.between(f.getPeriodEndDate(), target))
                       < Math.abs(ChronoUnit.DAYS.between(first.getPeriodEndDate(), target))) {
                first = f;
            }
        }
        double years = ChronoUnit.DAYS.between(first.getPeriodEndDate(), last.getPeriodEndDate()) / 365.25;
        if (years < 0.5) return Optional.empty();
        return Optional.of(new CagrWindow(first, last, years));
    }

    /**
     * Compound annual growth rate, in percent.
     *  - start <= 0: not meaningful (growth "from" a loss) -> absent.
     *  - end <= 0 with a positive start: the figure collapsed into a loss;
     *    returned as -100% so it lands in the lowest band. (v1 produced NaN
     *    here, which was then scored as the BEST band.)
     */
    static Optional<Double> cagr(BigDecimal startVal, BigDecimal endVal, double years) {
        if (startVal == null || endVal == null || years <= 0) return Optional.empty();
        double start = startVal.doubleValue();
        double end = endVal.doubleValue();
        if (start <= 0) return Optional.empty();
        if (end <= 0) return Optional.of(-100.0);
        double cagrPct = (Math.pow(end / start, 1.0 / years) - 1) * 100;
        return Double.isFinite(cagrPct) ? Optional.of(cagrPct) : Optional.empty();
    }

    static List<Double> closesAscending(List<Prices> pricesDesc) {
        return pricesDesc.reversed().stream()
                .map(Prices::getClose).filter(Objects::nonNull).map(BigDecimal::doubleValue).toList();
    }

    private static String describeCagr(double v) {
        return v <= -100.0 ? "turned negative (scored as a collapse)" : round(v) + "%";
    }

    private static String fmtYears(double years) {
        double r = Math.round(years * 10.0) / 10.0;
        return (r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r)) + " yr(s)";
    }

    private <T> Optional<BigDecimal> latest(List<T> list, java.util.function.Function<T, BigDecimal> getter) {
        if (list.isEmpty()) return Optional.empty();
        return Optional.ofNullable(getter.apply(list.get(list.size() - 1)));
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
    }

    private static double stdDev(List<Double> values) {
        double mean = values.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = values.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
