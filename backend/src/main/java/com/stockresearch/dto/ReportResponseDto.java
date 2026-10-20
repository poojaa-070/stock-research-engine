package com.stockresearch.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Full report payload. Fields up to bearCase are the v1 contract and are
 * unchanged; v2 fields are appended after them.
 */
public record ReportResponseDto(
        Long reportId,
        Long companyId,
        String companyName,
        String nseSymbol,
        LocalDateTime generatedAt,
        double overallScore,
        String verdict,
        double confidence,
        List<CategoryScoreDto> categories,
        String narrativeSummary,
        String disclaimer,
        Double currentPrice,           // live NSE last-traded-price when reachable, else latest scraped close; null if neither available
        boolean priceIsLive,           // true if currentPrice came from a live NSE quote just now, false if it's a cached close
        String priceAsOf,              // timestamp the currentPrice value is accurate as of (live quote time, or last close's trade date)
        Double priceChangePercent1y,   // null if less than ~a year of history available
        List<PricePointDto> priceHistory, // ascending by date, empty if no price data
        KeyRatiosDto keyRatios,
        String businessDescription,     // what they do / how they make money, null if not scraped
        QuarterlyResultDto latestQuarter, // null if no quarterly data yet
        TechnicalSnapshotDto technicalSnapshot, // null if not enough price history
        List<PeerSnapshotDto> sectorPeers,      // competitive position — same-sector companies
        List<NewsItemDto> recentNews,           // recent developments / potential catalysts
        String bullCase,   // strongest case FOR, grounded only in already-computed evidence
        String bearCase,    // strongest case AGAINST, same grounding rule

        // ---- v2 ----
        String sector,
        String industry,
        LiveQuoteDto liveQuote,                       // intraday detail when the price is live, else null
        List<FinancialPointDto> financialHistory,     // annual, ascending
        List<ShareholdingPointDto> shareholdingHistory, // ascending
        List<DataSourceStatusDto> dataFreshness,      // last scrape per source
        String narrativeSource,                       // "ai" | "rule-based"
        boolean qualitativeFlagsAvailable,            // false = AI layer didn't run; qualitative slots skipped
        Map<String, Double> categoryWeights,          // weights used for overallScore, for the what-if panel
        boolean servedFromCache                       // true = a recent report was reused instead of regenerated
) {
    public ReportResponseDto withServedFromCache(boolean cached) {
        return new ReportResponseDto(reportId, companyId, companyName, nseSymbol, generatedAt, overallScore, verdict,
                confidence, categories, narrativeSummary, disclaimer, currentPrice, priceIsLive, priceAsOf,
                priceChangePercent1y, priceHistory, keyRatios, businessDescription, latestQuarter, technicalSnapshot,
                sectorPeers, recentNews, bullCase, bearCase, sector, industry, liveQuote, financialHistory,
                shareholdingHistory, dataFreshness, narrativeSource, qualitativeFlagsAvailable, categoryWeights, cached);
    }
}
