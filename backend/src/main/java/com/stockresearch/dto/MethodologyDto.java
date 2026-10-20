package com.stockresearch.dto;

import java.util.List;
import java.util.Map;

/** Everything the scoring engine applies, as data, for the Methodology page. */
public record MethodologyDto(
        Map<String, Double> categoryWeights,
        List<Rule> rules,
        List<Flag> flags,
        Map<String, Double> confidenceMultipliers,
        List<VerdictBand> verdictBands,
        boolean skipLeverageForFinancials
) {
    public record Rule(String category, String metricKey, String sector, List<Double> thresholds,
                       List<Double> scores, double weight, boolean higherIsBetter) {}

    public record Flag(String flagKey, String category, String description, double effectAtHighConfidence) {}

    public record VerdictBand(double minScore, String verdict) {}
}
