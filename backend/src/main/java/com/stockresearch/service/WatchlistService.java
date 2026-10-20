package com.stockresearch.service;

import com.stockresearch.dto.CompanySummaryDto;
import com.stockresearch.dto.WatchlistItemDto;
import com.stockresearch.exception.NotFoundException;
import com.stockresearch.model.AnalysisReport;
import com.stockresearch.model.Company;
import com.stockresearch.model.Watchlist;
import com.stockresearch.repository.AnalysisReportRepository;
import com.stockresearch.repository.CompanyRepository;
import com.stockresearch.repository.PricesRepository;
import com.stockresearch.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Single-user watchlist (ROADMAP Phase 6, first step). Each row shows the
 * latest stored score and how it moved since the previous report, so the
 * watchlist doubles as a "what changed across everything I follow" view.
 */
@Service
public class WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);

    private final WatchlistRepository watchlistRepository;
    private final CompanyRepository companyRepository;
    private final AnalysisReportRepository analysisReportRepository;
    private final PricesRepository pricesRepository;
    private final ReportCache reportCache;

    public WatchlistService(WatchlistRepository watchlistRepository, CompanyRepository companyRepository,
                            AnalysisReportRepository analysisReportRepository, PricesRepository pricesRepository,
                            ReportCache reportCache) {
        this.watchlistRepository = watchlistRepository;
        this.companyRepository = companyRepository;
        this.analysisReportRepository = analysisReportRepository;
        this.pricesRepository = pricesRepository;
        this.reportCache = reportCache;
    }

    public List<WatchlistItemDto> list() {
        return watchlistRepository.findAllByOrderByAddedAtDesc().stream()
                .map(w -> companyRepository.findById(w.getCompanyId()).map(c -> toDto(w, c)))
                .flatMap(Optional::stream)
                .toList();
    }

    public boolean contains(Long companyId) {
        return watchlistRepository.existsByCompanyId(companyId);
    }

    @Transactional
    public WatchlistItemDto add(Long companyId, String note) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("No company with id " + companyId));
        Watchlist row = watchlistRepository.findByCompanyId(companyId).orElseGet(() -> {
            Watchlist w = new Watchlist();
            w.setCompanyId(companyId);
            w.setAddedAt(LocalDateTime.now());
            return w;
        });
        if (note != null) row.setNote(note.isBlank() ? null : note.trim());
        return toDto(watchlistRepository.save(row), company);
    }

    @Transactional
    public void remove(Long companyId) {
        watchlistRepository.findByCompanyId(companyId).ifPresent(watchlistRepository::delete);
    }

    /**
     * Optional background refresh of every watched company. Disabled by
     * default ("-"); enable with e.g. WATCHLIST_REFRESH_CRON="0 30 18 * * MON-FRI"
     * to re-score after NSE's close. Sequential on purpose — one scrape at a
     * time keeps us a polite guest on Screener/NSE.
     */
    @Scheduled(cron = "${app.watchlist.refresh-cron:-}", zone = "${app.watchlist.refresh-zone:Asia/Kolkata}")
    public void refreshAll() {
        List<Watchlist> rows = watchlistRepository.findAllByOrderByAddedAtDesc();
        log.info("Scheduled watchlist refresh: {} companies", rows.size());
        for (Watchlist w : rows) {
            try {
                reportCache.generate(w.getCompanyId(), true);
            } catch (Exception e) {
                log.warn("Scheduled refresh failed for company {}: {}", w.getCompanyId(), e.getMessage());
            }
        }
    }

    private WatchlistItemDto toDto(Watchlist w, Company c) {
        List<AnalysisReport> reports = analysisReportRepository.findByCompanyIdOrderByGeneratedAtDesc(c.getId());
        AnalysisReport latest = reports.isEmpty() ? null : reports.get(0);
        Double change = null;
        if (reports.size() >= 2 && reports.get(0).getOverallScore() != null && reports.get(1).getOverallScore() != null) {
            change = reports.get(0).getOverallScore().doubleValue() - reports.get(1).getOverallScore().doubleValue();
        }
        var lastPrice = pricesRepository.findTopByCompanyIdOrderByTradeDateDesc(c.getId());
        return new WatchlistItemDto(
                new CompanySummaryDto(c.getId(), c.getName(), c.getNseSymbol(), c.getBseCode(), c.getSector(), c.getIndustry()),
                w.getNote(),
                w.getAddedAt(),
                latest == null || latest.getOverallScore() == null ? null : latest.getOverallScore().doubleValue(),
                latest == null ? null : latest.getVerdict(),
                latest == null ? null : latest.getGeneratedAt(),
                change,
                lastPrice.map(p -> p.getClose() == null ? null : p.getClose().doubleValue()).orElse(null),
                lastPrice.map(p -> p.getTradeDate()).orElse(null));
    }
}
