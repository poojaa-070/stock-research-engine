package com.stockresearch.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Diff between the two most recent reports. Fields up to categoryDeltas are
 * the v1 contract; v2 appends verdicts and a per-category evidence diff
 * (ROADMAP Phase 4) so the user can see WHY a category moved.
 */
public record WhatChangedDto(
        Long companyId,
        LocalDateTime previousReportAt,
        LocalDateTime currentReportAt,
        double previousOverallScore,
        double currentOverallScore,
        List<CategoryDelta> categoryDeltas,
        // ---- v2 ----
        String previousVerdict,
        String currentVerdict,
        List<EvidenceChange> evidenceChanges
) {
    public record CategoryDelta(String category, double previousScore, double currentScore, double delta) {}

    /** Evidence statements that appeared in / dropped out of a category. Unchanged ones are omitted. */
    public record EvidenceChange(String category, List<String> added, List<String> removed) {}
}
