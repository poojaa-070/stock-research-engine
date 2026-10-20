package com.stockresearch.controller;

import com.stockresearch.dto.ReportHistoryPointDto;
import com.stockresearch.dto.ReportResponseDto;
import com.stockresearch.dto.WhatChangedDto;
import com.stockresearch.service.ReportCache;
import com.stockresearch.service.ReportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportCache reportCache;

    public ReportController(ReportService reportService, ReportCache reportCache) {
        this.reportService = reportService;
        this.reportCache = reportCache;
    }

    // POST /api/reports/generate/{companyId}[?force=true]
    // Runs a fresh scrape-to-score pass. v2: concurrent duplicate requests share
    // one generation, and a report from the last minute is reused unless force=true.
    @PostMapping("/generate/{companyId}")
    public ReportResponseDto generate(@PathVariable Long companyId,
                                      @RequestParam(defaultValue = "false") boolean force) {
        return reportCache.generate(companyId, force);
    }

    // GET /api/reports/latest/{companyId} — v2: what the report page loads.
    // Returns a report from the last 15 minutes if there is one, else generates.
    @GetMapping("/latest/{companyId}")
    public ReportResponseDto latest(@PathVariable Long companyId) {
        return reportCache.latest(companyId);
    }

    // GET /api/reports/history/{companyId}?limit=30 — v2: score over time, oldest first.
    @GetMapping("/history/{companyId}")
    public List<ReportHistoryPointDto> history(@PathVariable Long companyId,
                                               @RequestParam(defaultValue = "30") int limit) {
        return reportService.history(companyId, Math.min(limit, 200));
    }

    // GET /api/reports/what-changed/{companyId} — diffs latest vs previous report
    // (409 if fewer than two reports exist).
    @GetMapping("/what-changed/{companyId}")
    public WhatChangedDto whatChanged(@PathVariable Long companyId) {
        return reportService.whatChanged(companyId);
    }
}
