package com.stockresearch.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** One past report, for the score-over-time chart. */
public record ReportHistoryPointDto(
        Long reportId,
        LocalDateTime generatedAt,
        double overallScore,
        String verdict,
        double confidence,
        Map<String, Double> categoryScores
) {}
