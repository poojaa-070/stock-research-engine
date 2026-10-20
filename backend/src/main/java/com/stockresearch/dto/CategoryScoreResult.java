package com.stockresearch.dto;

import java.util.ArrayList;
import java.util.List;

public class CategoryScoreResult {
    public final String category;
    public double score;              // 0-100, weighted average of computed metric sub-scores
    public double dataCompleteness;    // fraction of expected metrics that were actually computable
    public final List<EvidenceItem> evidence = new ArrayList<>();

    public CategoryScoreResult(String category) {
        this.category = category;
    }

    public record EvidenceItem(String statement, String sourceRef, String sentiment) {}

    public void addEvidence(String statement, String sourceRef, String sentiment) {
        evidence.add(new EvidenceItem(statement, sourceRef, sentiment));
    }
}
