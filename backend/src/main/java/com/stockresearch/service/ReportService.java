package com.stockresearch.service;

import com.stockresearch.ai.ClaudeAiService;
import com.stockresearch.dto.*;
import com.stockresearch.exception.ConflictException;
import com.stockresearch.exception.NotFoundException;
import com.stockresearch.model.*;
import com.stockresearch.repository.*;
import com.stockresearch.scoring.MetricCalculator;
import com.stockresearch.scoring.ScoringEngine;
import com.stockresearch.scoring.Technicals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Builds a full research report for one company.
 *
 * v2 structure (behaviour of every v1 field is preserved):
 *  1. best-effort scraper refresh            — outside any transaction
 *  2. load stored facts, compute metrics, extract AI flags, score
 *  3. persist report + evidence               — one short transaction
 *  4. narrative, bull/bear, live quote, live peers — concurrently
 *  5. assemble display-only blocks (technicals, history, freshness)
 *
 * v1 ran everything inside one @Transactional method, holding a database
 * transaction open across several multi-second HTTP calls, and ran those
 * calls one after another.
 *
 * Callers should normally go through {@link ReportCache}, which de-duplicates
 * concurrent requests and reuses very recent reports.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    static final String DISCLAIMER = "This is an analytical research tool, not personalized financial advice.";

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "financial_health", "Financial Health",
            "business_quality", "Business Quality",
            "growth", "Growth",
            "valuation", "Valuation",
            "management", "Management",
            "risk", "Risk",
            "momentum", "Momentum"
    );

    private final CompanyRepository companyRepository;
    private final FinancialsRepository financialsRepository;
    private final RatiosRepository ratiosRepository;
    private final ShareholdingPatternRepository shareholdingPatternRepository;
    private final PricesRepository pricesRepository;
    private final NewsItemRepository newsItemRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final ReportEvidenceRepository reportEvidenceRepository;
    private final ScrapeMetadataRepository scrapeMetadataRepository;
    private final MetricCalculator metricCalculator;
    private final ScoringEngine scoringEngine;
    private final ClaudeAiService claudeAiService;
    private final ScraperClient scraperClient;
    private final TransactionTemplate transactionTemplate;
    // Virtual threads: these tasks spend their whole life blocked on HTTP.
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ReportService(CompanyRepository companyRepository, FinancialsRepository financialsRepository,
                         RatiosRepository ratiosRepository, ShareholdingPatternRepository shareholdingPatternRepository,
                         PricesRepository pricesRepository, NewsItemRepository newsItemRepository,
                         AnalysisReportRepository analysisReportRepository, ReportEvidenceRepository reportEvidenceRepository,
                         ScrapeMetadataRepository scrapeMetadataRepository,
                         MetricCalculator metricCalculator, ScoringEngine scoringEngine, ClaudeAiService claudeAiService,
                         ScraperClient scraperClient, PlatformTransactionManager transactionManager) {
        this.companyRepository = companyRepository;
        this.financialsRepository = financialsRepository;
        this.ratiosRepository = ratiosRepository;
        this.shareholdingPatternRepository = shareholdingPatternRepository;
        this.pricesRepository = pricesRepository;
        this.newsItemRepository = newsItemRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.reportEvidenceRepository = reportEvidenceRepository;
        this.scrapeMetadataRepository = scrapeMetadataRepository;
        this.metricCalculator = metricCalculator;
        this.scoringEngine = scoringEngine;
        this.claudeAiService = claudeAiService;
        this.scraperClient = scraperClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // =====================================================================
    // Report generation
    // =====================================================================

    public ReportResponseDto generateReport(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("No company with id " + companyId));

        try {
            scraperClient.forceRefresh(companyId);
        } catch (Exception e) {
            // Scraper service not running, or the live scrape failed (Screener/NSE
            // down, rate-limited...). Freshness is best-effort: the report still
            // generates from whatever's already in Postgres, so a demo-data-only
            // setup with no scraper running keeps working.
            log.debug("Scraper refresh skipped for company {}: {}", companyId, e.getMessage());
        }
        // Re-read: the refresh may have filled in sector, description, etc.
        company = companyRepository.findById(companyId).orElse(company);
        final Company c = company;

        List<Financials> annualFinancials = financialsRepository
                .findByCompanyIdAndPeriodTypeOrderByPeriodEndDateAsc(companyId, "annual");
        List<Financials> quarterlyFinancials = financialsRepository
                .findByCompanyIdAndPeriodTypeOrderByPeriodEndDateAsc(companyId, "quarterly");
        List<Ratios> ratiosHistory = ratiosRepository.findByCompanyIdOrderByPeriodEndDateAsc(companyId);
        List<ShareholdingPattern> shareholding = shareholdingPatternRepository
                .findByCompanyIdOrderByPeriodEndDateAsc(companyId);
        List<Prices> pricesDesc = pricesRepository.findTop260ByCompanyIdOrderByTradeDateDesc(companyId).stream()
                .filter(p -> p.getClose() != null).toList();

        // Peers for SCORING are DB-sourced (a score comparison needs companies we
        // hold ratios for). v1 bug: an imported company had sector "" and so
        // matched every other imported company; a blank sector now means no peers.
        List<Company> sectorPeerCompanies = hasText(c.getSector())
                ? companyRepository.findBySectorIgnoreCase(c.getSector()).stream()
                        .filter(p -> !p.getId().equals(companyId)).toList()
                : List.of();
        List<Ratios> sectorPeerLatest = sectorPeerCompanies.stream()
                .map(p -> ratiosRepository.findTopByCompanyIdOrderByPeriodEndDateDesc(p.getId()))
                .flatMap(Optional::stream)
                .toList();

        var inputs = metricCalculator.compute(c, annualFinancials, ratiosHistory, shareholding, pricesDesc, sectorPeerLatest);
        KeyRatiosDto keyRatios = buildKeyRatios(annualFinancials, ratiosHistory, shareholding);

        List<NewsItem> news = newsItemRepository.findByCompanyIdOrderByPublishedAtDesc(companyId);
        List<String> newsSnippets = news.stream()
                .limit(40) // bound the prompt; the newest items matter most
                .map(n -> n.getHeadline() + " [" + n.getSource() + ", " + n.getPublishedAt() + "]")
                .toList();
        var flagResult = claudeAiService.extractFlagsWithStatus(c.getName(), newsSnippets);

        Map<String, CategoryScoreResult> categoryScores =
                scoringEngine.scoreAllCategories(inputs, flagResult.flags(), flagResult.ran(), c.getSector());
        double overall = scoringEngine.overallScore(categoryScores);
        double confidence = scoringEngine.overallConfidence(categoryScores);
        String verdict = scoringEngine.verdictFor(overall);

        AnalysisReport report = transactionTemplate.execute(status ->
                persistReport(companyId, categoryScores, overall, confidence, verdict));

        // Independent network calls, run concurrently.
        CompletableFuture<String> narrativeF = async(() ->
                claudeAiService.summarizeReport(c.getName(), categoryScores, overall, verdict), "");
        CompletableFuture<ClaudeAiService.BullBearCase> bullBearF = async(() ->
                claudeAiService.generateBullBearCase(c.getName(), categoryScores), new ClaudeAiService.BullBearCase("", ""));
        CompletableFuture<ScraperClient.LiveQuote> quoteF = hasText(c.getNseSymbol())
                ? async(() -> scraperClient.getLiveQuote(c.getNseSymbol()), null)
                : CompletableFuture.completedFuture(null);
        CompletableFuture<List<PeerSnapshotDto>> peersF = async(() -> buildPeerSnapshots(c, sectorPeerCompanies), List.of());

        String narrative = narrativeF.join();
        String narrativeSource = "ai";
        if (!hasText(narrative)) {
            // AI unavailable (no key, API error, malformed reply): build a grounded
            // summary from the same computed scores/evidence instead of a blank.
            narrative = buildFallbackNarrative(c, categoryScores, overall, verdict, keyRatios);
            narrativeSource = "rule-based";
        }

        var bullBearAi = bullBearF.join();
        String bullCase = bullBearAi.bullCase();
        String bearCase = bullBearAi.bearCase();
        if (!hasText(bullCase) || !hasText(bearCase)) {
            var fallback = buildFallbackBullBear(c, categoryScores);
            if (!hasText(bullCase)) bullCase = fallback.bullCase();
            if (!hasText(bearCase)) bearCase = fallback.bearCase();
        }

        List<CategoryScoreDto> categoryDtos = categoryScores.entrySet().stream()
                .map(e -> new CategoryScoreDto(
                        e.getKey(), e.getValue().score, e.getValue().dataCompleteness,
                        e.getValue().evidence.stream()
                                .map(ev -> new EvidenceDto(ev.statement(), ev.sourceRef(), ev.sentiment()))
                                .toList()))
                .toList();

        // Price: last scraped close by default, NSE's live LTP when reachable.
        Double currentPrice = pricesDesc.isEmpty() ? null : pricesDesc.get(0).getClose().doubleValue();
        String priceAsOf = pricesDesc.isEmpty() ? null : pricesDesc.get(0).getTradeDate().toString();
        boolean priceIsLive = false;
        LiveQuoteDto liveQuoteDto = null;
        ScraperClient.LiveQuote liveQuote = quoteF.join();
        if (liveQuote != null && liveQuote.lastPrice() != null) {
            currentPrice = liveQuote.lastPrice();
            priceAsOf = liveQuote.asOf();
            priceIsLive = true;
            liveQuoteDto = new LiveQuoteDto(liveQuote.change(), liveQuote.percentChange(), liveQuote.previousClose(),
                    liveQuote.dayHigh(), liveQuote.dayLow(), liveQuote.marketState());
        }

        List<PricePointDto> priceHistory = pricesDesc.reversed().stream()
                .map(p -> new PricePointDto(p.getTradeDate(), p.getClose().doubleValue()))
                .toList();

        TechnicalSnapshotDto technicalSnapshot = buildTechnicalSnapshot(pricesDesc);
        if (technicalSnapshot != null && priceIsLive && currentPrice != null) {
            // Keep the historical 52w/DMA/RSI window but align "current price"
            // and "% from 52w high" with the live headline price.
            technicalSnapshot = technicalSnapshot.withCurrentPrice(currentPrice);
        }

        return new ReportResponseDto(
                report.getId(), companyId, c.getName(), c.getNseSymbol(),
                report.getGeneratedAt(), overall, verdict, confidence, categoryDtos,
                narrative, DISCLAIMER,
                currentPrice, priceIsLive, priceAsOf, priceChange1y(pricesDesc, currentPrice), priceHistory, keyRatios,
                c.getBusinessDescription(),
                buildLatestQuarter(quarterlyFinancials),
                technicalSnapshot,
                peersF.join(),
                buildRecentNews(news),
                bullCase, bearCase,
                blankToNull(c.getSector()), blankToNull(c.getIndustry()),
                liveQuoteDto,
                buildFinancialHistory(annualFinancials),
                buildShareholdingHistory(shareholding),
                buildDataFreshness(companyId),
                narrativeSource,
                flagResult.ran(),
                new LinkedHashMap<>(scoringEngine.categoryWeights()),
                false);
    }

    private AnalysisReport persistReport(Long companyId, Map<String, CategoryScoreResult> categoryScores,
                                         double overall, double confidence, String verdict) {
        AnalysisReport report = new AnalysisReport();
        report.setCompanyId(companyId);
        report.setGeneratedAt(LocalDateTime.now());
        report.setOverallScore(BigDecimal.valueOf(overall));
        report.setFinancialHealthScore(scoreOf(categoryScores, "financial_health"));
        report.setBusinessQualityScore(scoreOf(categoryScores, "business_quality"));
        report.setGrowthScore(scoreOf(categoryScores, "growth"));
        report.setValuationScore(scoreOf(categoryScores, "valuation"));
        report.setManagementScore(scoreOf(categoryScores, "management"));
        report.setRiskScore(scoreOf(categoryScores, "risk"));
        report.setMomentumScore(scoreOf(categoryScores, "momentum"));
        report.setVerdict(verdict);
        report.setConfidence(BigDecimal.valueOf(confidence));
        AnalysisReport saved = analysisReportRepository.save(report);

        List<ReportEvidence> rows = new ArrayList<>();
        for (var entry : categoryScores.entrySet()) {
            for (var ev : entry.getValue().evidence) {
                ReportEvidence row = new ReportEvidence();
                row.setReportId(saved.getId());
                row.setCategory(entry.getKey());
                row.setSentiment(ev.sentiment());
                row.setStatement(ev.statement());
                row.setSourceRef(truncate(ev.sourceRef(), 255)); // column is VARCHAR(255)
                rows.add(row);
            }
        }
        reportEvidenceRepository.saveAll(rows);
        return saved;
    }

    private <T> CompletableFuture<T> async(Supplier<T> task, T fallback) {
        return CompletableFuture.supplyAsync(task, ioExecutor).exceptionally(e -> {
            log.debug("Optional report step failed, using fallback: {}", e.getMessage());
            return fallback;
        });
    }

    // =====================================================================
    // History and "what changed?"
    // =====================================================================

    /** Past reports, oldest first, for the score-over-time chart. */
    public List<ReportHistoryPointDto> history(Long companyId, int limit) {
        if (!companyRepository.existsById(companyId)) {
            throw new NotFoundException("No company with id " + companyId);
        }
        List<AnalysisReport> desc = analysisReportRepository.findByCompanyIdOrderByGeneratedAtDesc(companyId);
        return desc.stream().limit(Math.max(1, limit)).toList().reversed().stream()
                .map(r -> {
                    Map<String, Double> cats = new LinkedHashMap<>();
                    putIfNotNull(cats, "financial_health", r.getFinancialHealthScore());
                    putIfNotNull(cats, "business_quality", r.getBusinessQualityScore());
                    putIfNotNull(cats, "growth", r.getGrowthScore());
                    putIfNotNull(cats, "valuation", r.getValuationScore());
                    putIfNotNull(cats, "management", r.getManagementScore());
                    putIfNotNull(cats, "risk", r.getRiskScore());
                    putIfNotNull(cats, "momentum", r.getMomentumScore());
                    return new ReportHistoryPointDto(r.getId(), r.getGeneratedAt(), toDouble(r.getOverallScore()),
                            r.getVerdict(), toDouble(r.getConfidence()), cats);
                })
                .toList();
    }

    public WhatChangedDto whatChanged(Long companyId) {
        List<AnalysisReport> history = analysisReportRepository.findByCompanyIdOrderByGeneratedAtDesc(companyId);
        if (history.size() < 2) {
            throw new ConflictException("Need at least two generated reports to compare — only "
                    + history.size() + " found. Regenerate the report later to see what changed.");
        }
        AnalysisReport current = history.get(0);
        AnalysisReport previous = history.get(1);

        List<WhatChangedDto.CategoryDelta> deltas = List.of(
                delta("financial_health", previous.getFinancialHealthScore(), current.getFinancialHealthScore()),
                delta("business_quality", previous.getBusinessQualityScore(), current.getBusinessQualityScore()),
                delta("growth", previous.getGrowthScore(), current.getGrowthScore()),
                delta("valuation", previous.getValuationScore(), current.getValuationScore()),
                delta("management", previous.getManagementScore(), current.getManagementScore()),
                delta("risk", previous.getRiskScore(), current.getRiskScore()),
                delta("momentum", previous.getMomentumScore(), current.getMomentumScore())
        );

        // Evidence diff (ROADMAP Phase 4): which statements are new vs dropped.
        // Statements embed their numbers, so a changed value shows as one
        // removed line plus one added line — exactly what moved, and why.
        Map<String, List<String>> prevEv = evidenceByCategory(previous.getId());
        Map<String, List<String>> currEv = evidenceByCategory(current.getId());
        Set<String> categories = new TreeSet<>();
        categories.addAll(prevEv.keySet());
        categories.addAll(currEv.keySet());
        List<WhatChangedDto.EvidenceChange> changes = new ArrayList<>();
        for (String cat : categories) {
            List<String> before = prevEv.getOrDefault(cat, List.of());
            List<String> after = currEv.getOrDefault(cat, List.of());
            List<String> added = after.stream().filter(s -> !before.contains(s)).toList();
            List<String> removed = before.stream().filter(s -> !after.contains(s)).toList();
            if (!added.isEmpty() || !removed.isEmpty()) {
                changes.add(new WhatChangedDto.EvidenceChange(cat, added, removed));
            }
        }

        return new WhatChangedDto(
                companyId, previous.getGeneratedAt(), current.getGeneratedAt(),
                toDouble(previous.getOverallScore()), toDouble(current.getOverallScore()), deltas,
                previous.getVerdict(), current.getVerdict(), changes);
    }

    private Map<String, List<String>> evidenceByCategory(Long reportId) {
        return reportEvidenceRepository.findByReportId(reportId).stream()
                .collect(Collectors.groupingBy(ReportEvidence::getCategory, TreeMap::new,
                        Collectors.mapping(ReportEvidence::getStatement, Collectors.toList())));
    }

    // =====================================================================
    // Display-only blocks (never feed the score)
    // =====================================================================

    /** Closest close on or before one year ago; null without ~a year of history. */
    static Double priceChange1y(List<Prices> pricesDesc, Double currentPrice) {
        if (pricesDesc.size() < 2 || currentPrice == null) return null;
        LocalDate latest = pricesDesc.get(0).getTradeDate();
        LocalDate target = latest.minusYears(1);
        LocalDate earliest = pricesDesc.get(pricesDesc.size() - 1).getTradeDate();
        if (earliest.isAfter(target.plusDays(7))) return null; // v1 labelled ~10 months as "1y"
        Prices yearAgo = null;
        for (Prices p : pricesDesc) { // descending: first one on/before target is the closest
            if (!p.getTradeDate().isAfter(target)) {
                yearAgo = p;
                break;
            }
        }
        if (yearAgo == null) yearAgo = pricesDesc.get(pricesDesc.size() - 1);
        double old = yearAgo.getClose().doubleValue();
        return old == 0 ? null : ((currentPrice - old) / old) * 100;
    }

    /**
     * Latest reported quarter, YoY vs the SAME quarter a year earlier
     * (matched by date, not just "the prior row") so seasonality cancels out.
     */
    private QuarterlyResultDto buildLatestQuarter(List<Financials> quarterlyFinancials) {
        if (quarterlyFinancials.isEmpty()) return null;
        Financials latest = quarterlyFinancials.get(quarterlyFinancials.size() - 1);

        Financials yearAgo = quarterlyFinancials.stream()
                .filter(f -> f != latest)
                .filter(f -> Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                        f.getPeriodEndDate(), latest.getPeriodEndDate().minusYears(1))) <= 20)
                .findFirst().orElse(null);

        Double operatingMargin = ratioPct(latest.getOperatingProfit(), latest.getRevenue());
        Double revenueYoy = null;
        Double netProfitYoy = null;
        if (yearAgo != null) {
            revenueYoy = growthPct(yearAgo.getRevenue(), latest.getRevenue());
            netProfitYoy = growthPct(yearAgo.getNetProfit(), latest.getNetProfit());
        }

        return new QuarterlyResultDto(
                latest.getPeriodEndDate(),
                nullableDouble(latest.getRevenue()),
                nullableDouble(latest.getNetProfit()),
                nullableDouble(latest.getOperatingProfit()),
                operatingMargin,
                revenueYoy,
                netProfitYoy,
                yearAgo != null ? yearAgo.getPeriodEndDate() : null);
    }

    /** 52-week range over the trailing calendar year; DMA/RSI via the shared Technicals helper. */
    private TechnicalSnapshotDto buildTechnicalSnapshot(List<Prices> pricesDesc) {
        if (pricesDesc.isEmpty()) return null;

        double currentPrice = pricesDesc.get(0).getClose().doubleValue();
        LocalDate cutoff = pricesDesc.get(0).getTradeDate().minusYears(1);
        List<Prices> lastYear = pricesDesc.stream().filter(p -> p.getTradeDate().isAfter(cutoff)).toList();

        double week52High = lastYear.stream()
                .map(p -> p.getHigh() != null ? p.getHigh() : p.getClose())
                .mapToDouble(BigDecimal::doubleValue).max().orElse(currentPrice);
        double week52Low = lastYear.stream()
                .map(p -> p.getLow() != null ? p.getLow() : p.getClose())
                .mapToDouble(BigDecimal::doubleValue).min().orElse(currentPrice);
        double percentFromHigh = week52High != 0 ? ((currentPrice - week52High) / week52High) * 100 : 0;

        List<Double> closesAsc = pricesDesc.reversed().stream().map(p -> p.getClose().doubleValue()).toList();
        Double dma50 = boxed(Technicals.sma(closesAsc, 50));
        Double dma200 = boxed(Technicals.sma(closesAsc, 200));
        Double rsi14 = boxed(Technicals.rsiWilder(closesAsc, 14));

        return new TechnicalSnapshotDto(currentPrice, week52High, week52Low, percentFromHigh, dma50, dma200, rsi14);
    }

    /**
     * Competitive-position table. Prefers a live Screener peer-comparison
     * scrape and falls back to same-sector companies in our own DB.
     * v2: live peers are matched against ALL our companies by NSE symbol (v1
     * only matched same-sector rows, so imported peers were rarely tappable),
     * and the company itself is removed (Screener lists it in its own table).
     */
    private List<PeerSnapshotDto> buildPeerSnapshots(Company company, List<Company> dbPeers) {
        if (hasText(company.getNseSymbol())) {
            try {
                var livePeers = scraperClient.getLiveSectorPeers(company.getNseSymbol());
                List<PeerSnapshotDto> rows = livePeers.stream()
                        .filter(p -> p.nseSymbol() == null || !p.nseSymbol().equalsIgnoreCase(company.getNseSymbol()))
                        .map(p -> {
                            Company matched = p.nseSymbol() == null ? null
                                    : companyRepository.findByNseSymbolIgnoreCase(p.nseSymbol()).orElse(null);
                            Double overallScore = matched == null ? null : latestScore(matched.getId());
                            return new PeerSnapshotDto(matched != null ? matched.getId() : null,
                                    p.name(), p.nseSymbol(), p.peRatio(), p.roce(), overallScore, p.cmp(), true);
                        })
                        .toList();
                if (!rows.isEmpty()) return rows;
            } catch (Exception e) {
                log.debug("Live peers unavailable for {}: {}", company.getNseSymbol(), e.getMessage());
            }
        }

        return dbPeers.stream().map(peer -> {
            Ratios latestRatios = ratiosRepository.findTopByCompanyIdOrderByPeriodEndDateDesc(peer.getId()).orElse(null);
            return new PeerSnapshotDto(
                    peer.getId(), peer.getName(), peer.getNseSymbol(),
                    latestRatios != null ? nullableDouble(latestRatios.getPeRatio()) : null,
                    latestRatios != null ? nullableDouble(latestRatios.getRoce()) : null,
                    latestScore(peer.getId()), null, false);
        }).toList();
    }

    private Double latestScore(Long companyId) {
        return analysisReportRepository.findTopByCompanyIdOrderByGeneratedAtDesc(companyId)
                .map(r -> toDouble(r.getOverallScore())).orElse(null);
    }

    private List<NewsItemDto> buildRecentNews(List<NewsItem> newsDesc) {
        return newsDesc.stream()
                .limit(8)
                .map(n -> new NewsItemDto(n.getHeadline(), n.getSource(), n.getPublishedAt(), n.getUrl()))
                .toList();
    }

    private List<FinancialPointDto> buildFinancialHistory(List<Financials> annualAsc) {
        return annualAsc.stream()
                .map(f -> new FinancialPointDto(
                        f.getPeriodEndDate(),
                        nullableDouble(f.getRevenue()),
                        nullableDouble(f.getNetProfit()),
                        nullableDouble(f.getOperatingProfit()),
                        nullableDouble(f.getEps()),
                        ratioPct(f.getOperatingProfit(), f.getRevenue()),
                        ratioPct(f.getNetProfit(), f.getRevenue())))
                .toList();
    }

    private List<ShareholdingPointDto> buildShareholdingHistory(List<ShareholdingPattern> asc) {
        return asc.stream()
                .map(h -> new ShareholdingPointDto(h.getPeriodEndDate(),
                        nullableDouble(h.getPromoterPct()), nullableDouble(h.getFiiPct()),
                        nullableDouble(h.getDiiPct()), nullableDouble(h.getPublicPct())))
                .toList();
    }

    private List<DataSourceStatusDto> buildDataFreshness(Long companyId) {
        List<DataSourceStatusDto> out = new ArrayList<>();
        for (String source : List.of("screener", "nse")) {
            scrapeMetadataRepository.findTopByCompanyIdAndSourceOrderByLastScrapedAtDesc(companyId, source)
                    .ifPresent(m -> out.add(new DataSourceStatusDto(source, m.getLastScrapedAt(), m.getStatus())));
        }
        return out;
    }

    /**
     * Raw, unscored "glance at the numbers" panel, built directly from the
     * fetched entities — deliberately independent of the scoring engine so a
     * display bug can never affect a score, and vice versa.
     * v2: growth figures use the same ~3-year window as the growth score.
     */
    private KeyRatiosDto buildKeyRatios(List<Financials> annualFinancials, List<Ratios> ratiosHistory,
                                        List<ShareholdingPattern> shareholding) {
        Ratios latestRatios = ratiosHistory.isEmpty() ? null : ratiosHistory.get(ratiosHistory.size() - 1);

        Double currentRatio = null;
        Double interestCoverage = null;
        if (!annualFinancials.isEmpty()) {
            Financials last = annualFinancials.get(annualFinancials.size() - 1);
            if (last.getCurrentAssets() != null && last.getCurrentLiabilities() != null
                    && last.getCurrentLiabilities().doubleValue() != 0) {
                currentRatio = last.getCurrentAssets().doubleValue() / last.getCurrentLiabilities().doubleValue();
            }
            if (last.getOperatingProfit() != null && last.getInterestExpense() != null
                    && last.getInterestExpense().doubleValue() != 0) {
                interestCoverage = last.getOperatingProfit().doubleValue() / last.getInterestExpense().doubleValue();
            }
        }

        Double epsGrowth3y = null;
        Double revenueGrowth3y = null;
        var window = threeYearWindow(annualFinancials);
        if (window != null) {
            epsGrowth3y = cagrPct(window[0].getEps(), window[1].getEps(), years(window));
            revenueGrowth3y = cagrPct(window[0].getRevenue(), window[1].getRevenue(), years(window));
        }

        Double promoterHolding = null;
        String promoterTrend = "n/a";
        List<ShareholdingPattern> withPromoter = shareholding.stream()
                .filter(h -> h.getPromoterPct() != null)
                .sorted(Comparator.comparing(ShareholdingPattern::getPeriodEndDate))
                .toList();
        if (!withPromoter.isEmpty()) {
            double latest = withPromoter.get(withPromoter.size() - 1).getPromoterPct().doubleValue();
            if (latest > 0) {
                promoterHolding = latest;
                if (withPromoter.size() >= 2) {
                    double first = withPromoter.get(0).getPromoterPct().doubleValue();
                    double delta = latest - first;
                    promoterTrend = delta > 0.5 ? "improving" : delta < -0.5 ? "declining" : "flat";
                }
            }
        }

        return new KeyRatiosDto(
                latestRatios != null ? nullableDouble(latestRatios.getRoe()) : null,
                latestRatios != null ? nullableDouble(latestRatios.getRoce()) : null,
                latestRatios != null ? nullableDouble(latestRatios.getDebtToEquity()) : null,
                currentRatio,
                interestCoverage,
                latestRatios != null ? nullableDouble(latestRatios.getPeRatio()) : null,
                latestRatios != null ? nullableDouble(latestRatios.getPbRatio()) : null,
                latestRatios != null ? nullableDouble(latestRatios.getPegRatio()) : null,
                latestRatios != null ? nullableDouble(latestRatios.getEvEbitda()) : null,
                latestRatios != null ? nullableDouble(latestRatios.getDividendYield()) : null,
                epsGrowth3y,
                revenueGrowth3y,
                promoterHolding,
                promoterTrend
        );
    }

    /** [first, last] annual rows ~3 years apart (or the full span if shorter); null if < 2 rows. */
    private static Financials[] threeYearWindow(List<Financials> asc) {
        if (asc.size() < 2) return null;
        Financials last = asc.get(asc.size() - 1);
        LocalDate target = last.getPeriodEndDate().minusYears(3);
        Financials first = asc.get(0);
        for (Financials f : asc) {
            if (f == last || f.getPeriodEndDate().isBefore(target.minusDays(120))) continue;
            long d = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(f.getPeriodEndDate(), target));
            long best = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(first.getPeriodEndDate(), target));
            if (d < best) first = f;
        }
        return new Financials[]{first, last};
    }

    private static double years(Financials[] window) {
        return java.time.temporal.ChronoUnit.DAYS.between(window[0].getPeriodEndDate(), window[1].getPeriodEndDate()) / 365.25;
    }

    // =====================================================================
    // Rule-based fallbacks for the AI narrative
    // =====================================================================

    private String categoryLabel(String key) {
        return CATEGORY_LABELS.getOrDefault(key, key);
    }

    /**
     * Deterministic fallback for the narrative summary when the AI call comes
     * back empty. Grounded entirely in facts already in the report.
     * v2: categories with no data are named as data gaps rather than being
     * picked as "the area to watch" on the strength of a 0 score.
     */
    private String buildFallbackNarrative(Company company, Map<String, CategoryScoreResult> categoryScores,
                                          double overall, String verdict, KeyRatiosDto keyRatios) {
        List<Map.Entry<String, CategoryScoreResult>> scored = categoryScores.entrySet().stream()
                .filter(e -> e.getValue().dataCompleteness > 0)
                .sorted(Comparator.comparingDouble(e -> e.getValue().score))
                .toList();
        List<String> gaps = categoryScores.entrySet().stream()
                .filter(e -> e.getValue().dataCompleteness <= 0)
                .map(e -> categoryLabel(e.getKey())).toList();

        StringBuilder sb = new StringBuilder();
        sb.append(company.getName()).append(" scores ").append(Math.round(overall))
                .append("/100 overall (").append(verdict).append("). ");

        if (!scored.isEmpty()) {
            var highest = scored.get(scored.size() - 1);
            var lowest = scored.get(0);
            sb.append("Its strongest area is ").append(categoryLabel(highest.getKey()))
                    .append(" at ").append(Math.round(highest.getValue().score)).append("/100");
            firstEvidence(highest.getValue()).ifPresent(s -> sb.append(" — ").append(s));
            sb.append(". ");
            if (scored.size() > 1) {
                sb.append("The area to watch is ").append(categoryLabel(lowest.getKey()))
                        .append(" at ").append(Math.round(lowest.getValue().score)).append("/100");
                firstEvidence(lowest.getValue()).ifPresent(s -> sb.append(" — ").append(s));
                sb.append(". ");
            }
        }
        if (!gaps.isEmpty()) {
            sb.append("No usable data yet for ").append(String.join(", ", gaps))
                    .append(", so those categories are left out of the overall score. ");
        }

        if (keyRatios != null) {
            List<String> ratioBits = new ArrayList<>();
            if (keyRatios.peRatio() != null) ratioBits.add("a P/E of " + fmt1(keyRatios.peRatio()));
            if (keyRatios.roePercent() != null) ratioBits.add("ROE of " + fmt1(keyRatios.roePercent()) + "%");
            if (keyRatios.debtToEquity() != null) ratioBits.add("debt/equity of " + fmt1(keyRatios.debtToEquity()));
            if (keyRatios.dividendYieldPercent() != null) ratioBits.add("a dividend yield of " + fmt1(keyRatios.dividendYieldPercent()) + "%");
            if (!ratioBits.isEmpty()) {
                sb.append("Headline ratios show ").append(String.join(", ", ratioBits)).append(". ");
            }
        }

        if (hasText(company.getBusinessDescription())) {
            sb.append(truncate(company.getBusinessDescription(), 220)).append(" ");
        }

        sb.append(DISCLAIMER);
        return sb.toString();
    }

    private ClaudeAiService.BullBearCase buildFallbackBullBear(Company company,
                                                              Map<String, CategoryScoreResult> categoryScores) {
        List<Map.Entry<String, CategoryScoreResult>> sorted = categoryScores.entrySet().stream()
                .filter(e -> e.getValue().dataCompleteness > 0)
                .sorted(Comparator.comparingDouble(e -> e.getValue().score))
                .toList();
        if (sorted.isEmpty()) {
            String thin = "There isn't enough scraped data for " + company.getName()
                    + " yet to make a case either way.";
            return new ClaudeAiService.BullBearCase(thin, thin);
        }

        var weakest = sorted.subList(0, Math.min(2, sorted.size()));
        var strongest = sorted.subList(Math.max(0, sorted.size() - 2), sorted.size()).reversed();

        StringBuilder bull = new StringBuilder();
        bull.append(company.getName()).append("'s strongest evidence comes from its ")
                .append(strongest.stream()
                        .map(e -> categoryLabel(e.getKey()) + " (" + Math.round(e.getValue().score) + "/100)")
                        .collect(Collectors.joining(" and ")))
                .append(". ");
        for (var e : strongest) firstEvidence(e.getValue()).ifPresent(s -> bull.append(s).append(". "));

        StringBuilder bear = new StringBuilder();
        bear.append("The weaker spots are ")
                .append(weakest.stream()
                        .map(e -> categoryLabel(e.getKey()) + " (" + Math.round(e.getValue().score) + "/100)")
                        .collect(Collectors.joining(" and ")))
                .append(". ");
        for (var e : weakest) {
            var ev = firstEvidence(e.getValue());
            if (ev.isPresent()) {
                bear.append(ev.get()).append(". ");
            }
            if (e.getValue().dataCompleteness < 0.6) {
                bear.append(categoryLabel(e.getKey())).append(" data completeness is only ")
                        .append(Math.round(e.getValue().dataCompleteness * 100))
                        .append("%, so this reading rests on partial data. ");
            }
        }
        long gaps = categoryScores.values().stream().filter(r -> r.dataCompleteness <= 0).count();
        if (gaps > 0) {
            bear.append(gaps).append(" categor").append(gaps == 1 ? "y has" : "ies have")
                    .append(" no usable data at all yet. ");
        }

        return new ClaudeAiService.BullBearCase(bull.toString().trim(), bear.toString().trim());
    }

    /** Most informative evidence line: prefers a scored metric over a neutral placeholder. */
    private Optional<String> firstEvidence(CategoryScoreResult r) {
        return r.evidence.stream()
                .filter(ev -> !"neutral".equals(ev.sentiment()))
                .map(CategoryScoreResult.EvidenceItem::statement)
                .findFirst()
                .or(() -> r.evidence.stream().map(CategoryScoreResult.EvidenceItem::statement).findFirst());
    }

    // =====================================================================
    // Small helpers
    // =====================================================================

    private WhatChangedDto.CategoryDelta delta(String category, BigDecimal prev, BigDecimal curr) {
        double p = prev == null ? 0 : prev.doubleValue();
        double c = curr == null ? 0 : curr.doubleValue();
        return new WhatChangedDto.CategoryDelta(category, p, c, c - p);
    }

    private BigDecimal scoreOf(Map<String, CategoryScoreResult> map, String key) {
        CategoryScoreResult r = map.get(key);
        return r == null ? null : BigDecimal.valueOf(r.score);
    }

    private static void putIfNotNull(Map<String, Double> map, String key, BigDecimal v) {
        if (v != null) map.put(key, v.doubleValue());
    }

    private static Double ratioPct(BigDecimal num, BigDecimal den) {
        if (num == null || den == null || den.doubleValue() == 0) return null;
        return num.doubleValue() / den.doubleValue() * 100;
    }

    private static Double growthPct(BigDecimal from, BigDecimal to) {
        if (from == null || to == null || from.doubleValue() == 0) return null;
        // Divide by |from| so a loss narrowing (-100 -> -50) reads as +50%, not -50%.
        return (to.doubleValue() - from.doubleValue()) / Math.abs(from.doubleValue()) * 100;
    }

    private static Double cagrPct(BigDecimal start, BigDecimal end, double years) {
        if (start == null || end == null || years <= 0 || start.doubleValue() <= 0) return null;
        if (end.doubleValue() <= 0) return null; // shown as a dash here; the score treats it as a collapse
        return (Math.pow(end.doubleValue() / start.doubleValue(), 1.0 / years) - 1) * 100;
    }

    private static Double boxed(OptionalDouble v) {
        return v.isPresent() ? v.getAsDouble() : null;
    }

    private static Double nullableDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private static double toDouble(BigDecimal v) {
        return v == null ? 0 : v.doubleValue();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return hasText(s) ? s : null;
    }

    private static String fmt1(Double v) {
        if (v == null) return "N/A";
        return String.valueOf(Math.round(v * 10.0) / 10.0);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        int cut = s.lastIndexOf(' ', maxLen - 1);
        if (cut <= 0) cut = maxLen - 1;
        return s.substring(0, cut).trim() + "…";
    }
}
