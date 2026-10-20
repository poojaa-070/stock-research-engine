package com.stockresearch.dto;

import java.util.List;

/** N-way comparison (ROADMAP Phase 5). values[i] belongs to companies[i]. */
public record MultiCompareResponseDto(
        List<Column> companies,
        List<Row> rows
) {
    public record Column(CompanySummaryDto company, Double overallScore, String verdict) {}

    /**
     * bestIndices lists every column holding the best value (several on a tie);
     * empty when fewer than two columns have data or the row has no direction.
     * higherIsBetter is null for informational rows such as live price.
     */
    public record Row(String metricLabel, List<String> values, List<Integer> bestIndices, Boolean higherIsBetter) {}
}
