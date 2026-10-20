package com.stockresearch.dto;

/**
 * Raw, unscored professional metrics for a "glance at the numbers" panel —
 * distinct from the 0-100 category scores. A serious investor wants to see
 * the actual ROE %, actual D/E ratio, etc., not just an abstracted score.
 * Every field is nullable and left null (not guessed/defaulted) when the
 * underlying data isn't available, same "absent, not faked" principle as
 * the scoring engine.
 */
public record KeyRatiosDto(
        Double roePercent,
        Double rocePercent,
        Double debtToEquity,
        Double currentRatio,
        Double interestCoverageX,
        Double peRatio,
        Double pbRatio,
        Double pegRatio,
        Double evEbitda,
        Double dividendYieldPercent,
        Double epsGrowthPercent3y,
        Double revenueGrowthPercent3y,
        Double promoterHoldingPercent,
        String promoterHoldingTrend // "improving" | "declining" | "flat" | "n/a"
) {}
