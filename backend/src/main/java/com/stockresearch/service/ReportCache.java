package com.stockresearch.service;

import com.stockresearch.dto.ReportResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sits in front of {@link ReportService} and solves two v1 problems:
 *
 *  1. Duplicate generation. React StrictMode mounts effects twice in dev, so
 *     every report page view fired two POSTs, scraped twice, called Claude six
 *     times and stored two near-identical reports (which then made "what
 *     changed?" compare a report with its own twin). Concurrent requests for
 *     the same company now share ONE generation.
 *
 *  2. Unbounded scrape traffic (ROADMAP Phase 6). A report generated in the
 *     last {@code reuse-seconds} is reused by POST /generate unless the caller
 *     forces a fresh one; GET /latest reuses anything within the cache TTL.
 *
 * In-memory and per-instance on purpose: this is a single-user tool. A
 * multi-instance deployment would move this to Redis or similar.
 */
@Service
public class ReportCache {

    private record Entry(ReportResponseDto report, Instant createdAt) {}

    private final ReportService reportService;
    private final Duration ttl;
    private final Duration reuseWindow;
    private final int maxEntries;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<ReportResponseDto>> inFlight = new ConcurrentHashMap<>();

    public ReportCache(ReportService reportService,
                       @Value("${app.reports.cache-minutes:15}") long cacheMinutes,
                       @Value("${app.reports.reuse-seconds:60}") long reuseSeconds,
                       @Value("${app.reports.cache-max-entries:200}") int maxEntries) {
        this.reportService = reportService;
        this.ttl = Duration.ofMinutes(cacheMinutes);
        this.reuseWindow = Duration.ofSeconds(reuseSeconds);
        this.maxEntries = Math.max(1, maxEntries);
    }

    /** GET semantics: a cached report younger than the TTL, else a fresh one. */
    public ReportResponseDto latest(Long companyId) {
        return fresh(companyId, ttl).map(r -> r.withServedFromCache(true))
                .orElseGet(() -> generateShared(companyId));
    }

    /**
     * POST semantics: always a new report, except that one generated within
     * the short reuse window is returned instead (unless {@code force}).
     */
    public ReportResponseDto generate(Long companyId, boolean force) {
        if (!force) {
            Optional<ReportResponseDto> recent = fresh(companyId, reuseWindow);
            if (recent.isPresent()) return recent.get().withServedFromCache(true);
        }
        return generateShared(companyId);
    }

    /** Cached report if present and within the TTL; never triggers generation. */
    public Optional<ReportResponseDto> peek(Long companyId) {
        return fresh(companyId, ttl);
    }

    public void invalidate(Long companyId) {
        cache.remove(companyId);
    }

    private Optional<ReportResponseDto> fresh(Long companyId, Duration maxAge) {
        Entry e = cache.get(companyId);
        if (e == null || Instant.now().isAfter(e.createdAt().plus(maxAge))) return Optional.empty();
        return Optional.of(e.report());
    }

    /**
     * The first caller generates in its own thread; concurrent callers for the
     * same company wait on the same future. The map is only mutated outside
     * any computeIfAbsent callback, so there is no recursive-update hazard.
     */
    private ReportResponseDto generateShared(Long companyId) {
        CompletableFuture<ReportResponseDto> mine = new CompletableFuture<>();
        CompletableFuture<ReportResponseDto> existing = inFlight.putIfAbsent(companyId, mine);
        if (existing != null) {
            return join(existing);
        }
        try {
            ReportResponseDto report = reportService.generateReport(companyId);
            put(companyId, report);
            mine.complete(report);
            return report;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(companyId, mine);
        }
    }

    private void put(Long companyId, ReportResponseDto report) {
        cache.put(companyId, new Entry(report, Instant.now()));
        if (cache.size() > maxEntries) {
            cache.entrySet().stream()
                    .min(Comparator.comparing(en -> en.getValue().createdAt()))
                    .ifPresent(oldest -> cache.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    private static ReportResponseDto join(CompletableFuture<ReportResponseDto> f) {
        try {
            return f.join().withServedFromCache(true);
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }
}
