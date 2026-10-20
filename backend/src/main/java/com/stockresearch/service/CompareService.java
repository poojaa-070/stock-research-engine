package com.stockresearch.service;

import com.stockresearch.dto.*;
import com.stockresearch.exception.NotFoundException;
import com.stockresearch.model.AnalysisReport;
import com.stockresearch.model.Company;
import com.stockresearch.model.Ratios;
import com.stockresearch.repository.AnalysisReportRepository;
import com.stockresearch.repository.CompanyRepository;
import com.stockresearch.repository.RatiosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Pairwise and N-way company comparison.
 *
 * v2 fixes: a company that had never been analysed was compared with an
 * overall score of 0.0 (so it always "lost"); a report is now generated for
 * it first. compareByName no longer scrapes each company three times
 * (import + forceRefresh + report refresh) — import, then one report pass.
 */
@Service
public class CompareService {

    private static final Logger log = LoggerFactory.getLogger(CompareService.class);
    public static final int MAX_MULTI = 6;

    private final CompanyRepository companyRepository;
    private final RatiosRepository ratiosRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final ScraperClient scraperClient;
    private final ReportCache reportCache;
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public CompareService(CompanyRepository companyRepository, RatiosRepository ratiosRepository,
                          AnalysisReportRepository analysisReportRepository, ScraperClient scraperClient,
                          ReportCache reportCache) {
        this.companyRepository = companyRepository;
        this.ratiosRepository = ratiosRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.scraperClient = scraperClient;
        this.reportCache = reportCache;
    }

    /** One comparable metric: how to read it and which direction is better (null = informational). */
    private record Metric(String label, Function<Ratios, BigDecimal> getter, Boolean higherIsBetter) {}

    private static final List<Metric> RATIO_METRICS = List.of(
            new Metric("P/E ratio", Ratios::getPeRatio, false),
            new Metric("P/B ratio", Ratios::getPbRatio, false),
            new Metric("EV/EBITDA", Ratios::getEvEbitda, false),
            new Metric("PEG ratio", Ratios::getPegRatio, false),
            new Metric("ROE (%)", Ratios::getRoe, true),
            new Metric("ROCE (%)", Ratios::getRoce, true),
            new Metric("Debt/Equity", Ratios::getDebtToEquity, false),
            new Metric("Dividend yield (%)", Ratios::getDividendYield, true)
    );

    /** Everything a comparison column needs, fetched once per company. */
    private record Side(Company company, Ratios ratios, Double score, String verdict, Double livePrice) {}

    // ---------------------------------------------------------------------
    // Pairwise (v1 contract unchanged)
    // ---------------------------------------------------------------------

    /**
     * Compare-by-name: takes two free-text names, resolves each one live via
     * the scraper's Screener import (registering it if new), then compares.
     */
    public CompareResponseDto compareByName(String nameA, String nameB) {
        var importedA = scraperClient.importCompany(nameA);
        var importedB = scraperClient.importCompany(nameB);
        // Import has just scraped both; a fresh report gives scores on that data.
        ensureFreshReport(importedA.companyId());
        ensureFreshReport(importedB.companyId());
        return compare(importedA.companyId(), importedB.companyId());
    }

    public CompareResponseDto compare(Long companyIdA, Long companyIdB) {
        List<Side> sides = loadSides(List.of(companyIdA, companyIdB));
        Side a = sides.get(0);
        Side b = sides.get(1);

        List<CompareRowDto> rows = new ArrayList<>();
        if (a.livePrice() != null || b.livePrice() != null) {
            rows.add(new CompareRowDto("CMP (live, ₹)", fmt(a.livePrice()), fmt(b.livePrice()), "N/A"));
        }
        for (Metric m : RATIO_METRICS) {
            Double va = val(a.ratios(), m.getter());
            Double vb = val(b.ratios(), m.getter());
            rows.add(new CompareRowDto(m.label(), fmt(va), fmt(vb), pairWinner(va, vb, m.higherIsBetter())));
        }
        rows.add(new CompareRowDto("Overall research score", fmt(a.score()), fmt(b.score()),
                pairWinner(a.score(), b.score(), true)));

        String overallWinner;
        if (a.score() == null || b.score() == null) {
            overallWinner = "N/A";
        } else if (a.score().doubleValue() == b.score().doubleValue()) {
            overallWinner = "Tie";
        } else {
            overallWinner = a.score() > b.score() ? a.company().getName() : b.company().getName();
        }

        return new CompareResponseDto(toSummary(a.company()), toSummary(b.company()),
                a.score() == null ? 0 : a.score(), b.score() == null ? 0 : b.score(), overallWinner, rows);
    }

    // ---------------------------------------------------------------------
    // N-way (ROADMAP Phase 5)
    // ---------------------------------------------------------------------

    public MultiCompareResponseDto compareMany(List<Long> companyIds) {
        List<Long> ids = companyIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() < 2) {
            throw new IllegalArgumentException("Pick at least two different companies to compare.");
        }
        if (ids.size() > MAX_MULTI) {
            throw new IllegalArgumentException("You can compare up to " + MAX_MULTI + " companies at once.");
        }
        List<Side> sides = loadSides(ids);

        List<MultiCompareResponseDto.Row> rows = new ArrayList<>();
        List<Double> prices = sides.stream().map(Side::livePrice).toList();
        if (prices.stream().anyMatch(Objects::nonNull)) {
            rows.add(multiRow("CMP (live, ₹)", prices, null));
        }
        for (Metric m : RATIO_METRICS) {
            rows.add(multiRow(m.label(), sides.stream().map(s -> val(s.ratios(), m.getter())).toList(), m.higherIsBetter()));
        }
        rows.add(multiRow("Overall research score", sides.stream().map(Side::score).toList(), true));

        List<MultiCompareResponseDto.Column> columns = sides.stream()
                .map(s -> new MultiCompareResponseDto.Column(toSummary(s.company()), s.score(), s.verdict()))
                .toList();
        return new MultiCompareResponseDto(columns, rows);
    }

    private MultiCompareResponseDto.Row multiRow(String label, List<Double> values, Boolean higherIsBetter) {
        List<String> shown = values.stream().map(this::fmt).toList();
        List<Integer> best = new ArrayList<>();
        long present = values.stream().filter(Objects::nonNull).count();
        if (higherIsBetter != null && present >= 2) {
            double target = values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue)
                    .reduce(higherIsBetter ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY,
                            higherIsBetter ? Math::max : Math::min);
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i) != null && values.get(i) == target) best.add(i);
            }
            if (best.size() == present) best.clear(); // everyone tied: no winner to mark
        }
        return new MultiCompareResponseDto.Row(label, shown, best, higherIsBetter);
    }

    // ---------------------------------------------------------------------
    // Shared loading
    // ---------------------------------------------------------------------

    private List<Side> loadSides(List<Long> ids) {
        List<Company> companies = ids.stream()
                .map(id -> companyRepository.findById(id)
                        .orElseThrow(() -> new NotFoundException("No company with id " + id)))
                .toList();

        // Live quotes are independent network calls: fetch them concurrently.
        List<CompletableFuture<Double>> quotes = companies.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> liveQuoteOrNull(c.getNseSymbol()), ioExecutor))
                .toList();

        List<Side> sides = new ArrayList<>();
        for (int i = 0; i < companies.size(); i++) {
            Company c = companies.get(i);
            AnalysisReport latest = ensureStoredReport(c.getId());
            Ratios r = ratiosRepository.findTopByCompanyIdOrderByPeriodEndDateDesc(c.getId()).orElse(null);
            sides.add(new Side(c, r,
                    latest == null || latest.getOverallScore() == null ? null : latest.getOverallScore().doubleValue(),
                    latest == null ? null : latest.getVerdict(),
                    quotes.get(i).join()));
        }
        return sides;
    }

    /** Latest stored report, generating one first if the company was never analysed. */
    private AnalysisReport ensureStoredReport(Long companyId) {
        Optional<AnalysisReport> latest = analysisReportRepository.findTopByCompanyIdOrderByGeneratedAtDesc(companyId);
        if (latest.isPresent()) return latest.get();
        ensureFreshReport(companyId);
        return analysisReportRepository.findTopByCompanyIdOrderByGeneratedAtDesc(companyId).orElse(null);
    }

    private void ensureFreshReport(Long companyId) {
        try {
            reportCache.latest(companyId);
        } catch (Exception e) {
            // Comparison still works on ratios alone; the score row shows N/A.
            log.warn("Couldn't generate a report for company {} during compare: {}", companyId, e.getMessage());
        }
    }

    private Double liveQuoteOrNull(String nseSymbol) {
        if (nseSymbol == null || nseSymbol.isBlank()) return null;
        try {
            return scraperClient.getLiveQuote(nseSymbol).lastPrice();
        } catch (Exception e) {
            return null;
        }
    }

    private Double val(Ratios r, Function<Ratios, BigDecimal> getter) {
        if (r == null) return null;
        BigDecimal v = getter.apply(r);
        return v == null ? null : v.doubleValue();
    }

    private String pairWinner(Double a, Double b, Boolean higherIsBetter) {
        if (a == null || b == null || higherIsBetter == null) return "N/A";
        if (a.doubleValue() == b.doubleValue()) return "Tie";
        boolean aBetter = higherIsBetter ? a > b : a < b;
        return aBetter ? "A" : "B";
    }

    private String fmt(Double v) {
        return v == null ? "N/A" : String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    private CompanySummaryDto toSummary(Company c) {
        return new CompanySummaryDto(c.getId(), c.getName(), c.getNseSymbol(), c.getBseCode(), c.getSector(), c.getIndustry());
    }
}
