package com.stockresearch.dto;

/**
 * A single structured flag extracted by Claude from scraped text (annual
 * report excerpts, news headlines, concall summaries). This is the ONLY
 * shape of output the AI layer is allowed to produce for anything that
 * feeds the score — never a bare number.
 *
 * The deterministic point-deduction mapping for each flagKey lives in
 * FlagDeductionRules (scoring package), not here and not in the prompt.
 */
public record QualitativeFlag(
        String flagKey,        // e.g. "customer_concentration_risk", "moat_erosion", "regulatory_overhang"
        boolean present,
        String confidence,     // "low" | "medium" | "high"
        String evidenceQuote,  // verbatim snippet the flag was derived from
        String sourceRef       // e.g. "News: Economic Times, 2026-07-20"
) {}
