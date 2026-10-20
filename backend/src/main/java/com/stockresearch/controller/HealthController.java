package com.stockresearch.controller;

import com.stockresearch.ai.ClaudeAiService;
import com.stockresearch.dto.HealthDto;
import com.stockresearch.repository.CompanyRepository;
import com.stockresearch.service.ScraperClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * v2: GET /api/health — this app is three processes (Postgres, this API, the
 * Python scraper) plus an optional API key. The header status dot uses this to
 * say which piece is missing instead of every screen failing mysteriously.
 * Returns 503 only when the database is down; scraper/AI are optional.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final CompanyRepository companyRepository;
    private final ScraperClient scraperClient;
    private final ClaudeAiService claudeAiService;

    public HealthController(CompanyRepository companyRepository, ScraperClient scraperClient,
                            ClaudeAiService claudeAiService) {
        this.companyRepository = companyRepository;
        this.scraperClient = scraperClient;
        this.claudeAiService = claudeAiService;
    }

    @GetMapping
    public ResponseEntity<HealthDto> health() {
        boolean dbUp;
        try {
            companyRepository.count();
            dbUp = true;
        } catch (Exception e) {
            dbUp = false;
        }
        HealthDto body = new HealthDto(
                dbUp ? "ok" : "degraded",
                dbUp ? "up" : "down",
                scraperClient.isReachable() ? "up" : "down",
                claudeAiService.isConfigured() ? "configured" : "not configured",
                LocalDateTime.now());
        return ResponseEntity.status(dbUp ? 200 : 503).body(body);
    }
}
