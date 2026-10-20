package com.stockresearch.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * All deterministically-computed raw metric values for one company, keyed by
 * the same metric_key used in scoring_rules (e.g. "roce_latest",
 * "debt_to_equity", "revenue_cagr_3y").
 *
 * Using a map instead of one field per metric is deliberate: it lets us add
 * a new metric by adding a scoring_rules row + a MetricCalculator entry,
 * without touching this class or the ScoringEngine's aggregation loop.
 *
 * A metric that is genuinely not computable from available data (see
 * MetricCalculator's comments for which ones today) is simply absent from
 * the map — the ScoringEngine renormalizes remaining weights within the
 * category rather than treating "missing" as zero or as a mid-scale guess.
 */
public class MetricInputs {

    private final Map<String, Double> values = new HashMap<>();
    private final Map<String, String> evidenceNotes = new HashMap<>();
    private final Map<String, String> evidenceSources = new HashMap<>();

    public void put(String metricKey, double value, String evidenceNote, String sourceRef) {
        values.put(metricKey, value);
        evidenceNotes.put(metricKey, evidenceNote);
        evidenceSources.put(metricKey, sourceRef);
    }

    public Optional<Double> get(String metricKey) {
        return Optional.ofNullable(values.get(metricKey));
    }

    public boolean has(String metricKey) {
        return values.containsKey(metricKey);
    }

    public String evidenceNoteFor(String metricKey) {
        return evidenceNotes.getOrDefault(metricKey, "");
    }

    public String sourceFor(String metricKey) {
        return evidenceSources.getOrDefault(metricKey, "");
    }

    public Map<String, Double> asMap() {
        return values;
    }
}
