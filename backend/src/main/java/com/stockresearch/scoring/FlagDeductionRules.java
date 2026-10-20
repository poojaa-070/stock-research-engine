package com.stockresearch.scoring;

import com.stockresearch.dto.QualitativeFlag;

import java.util.List;
import java.util.Map;

/**
 * Converts AI-extracted QualitativeFlags into point deductions from a 100
 * baseline, deterministically. Claude never sees or sets these numbers —
 * it only ever outputs {flagKey, present, confidence, evidenceQuote}.
 *
 * This is the piece that makes the "AI explains, code scores" boundary from
 * the project brief concrete and auditable: change a deduction, and every
 * future report reflects it immediately, without touching a prompt.
 *
 * Flags are grouped by which scoring category they're allowed to affect
 * (FLAG_CATEGORY below). This matters: earlier, Risk had two separate
 * qualitative slots (regulatory_flag, commodity_exposure_flag) that BOTH
 * applied the full flag list, silently double-counting every risk flag.
 * Now each flag belongs to exactly one category, and applyFlags(...) filters
 * to only the flags relevant to the category being scored.
 *
 * Covers the professional-investor qualitative checklist:
 *  - Business Quality: moat type (network effects, switching costs, cost
 *    advantage, intangible assets), market share, industry attractiveness,
 *    and Porter's-Five-Forces-style competitive intensity.
 *  - Management: governance and capital allocation (credibility,
 *    related-party transactions, auditor qualifications, buyback/capital
 *    allocation discipline).
 *  - Risk: regulatory, commodity, customer concentration, and
 *    supplier/buyer bargaining power.
 */
public final class FlagDeductionRules {

    private FlagDeductionRules() {}

    public enum FlagCategory { BUSINESS_QUALITY, MANAGEMENT, RISK }

    private static final Map<String, FlagCategory> FLAG_CATEGORY = Map.ofEntries(
            // --- Business Quality: moat, market position, industry structure ---
            Map.entry("moat_erosion", FlagCategory.BUSINESS_QUALITY),
            Map.entry("market_share_gain", FlagCategory.BUSINESS_QUALITY),
            Map.entry("durable_pricing_power", FlagCategory.BUSINESS_QUALITY),
            Map.entry("network_effects_present", FlagCategory.BUSINESS_QUALITY),
            Map.entry("high_switching_costs", FlagCategory.BUSINESS_QUALITY),
            Map.entry("cost_leadership_advantage", FlagCategory.BUSINESS_QUALITY),
            Map.entry("strong_intangible_assets", FlagCategory.BUSINESS_QUALITY),
            Map.entry("large_addressable_market", FlagCategory.BUSINESS_QUALITY),
            Map.entry("intense_competitive_rivalry", FlagCategory.BUSINESS_QUALITY),
            Map.entry("high_threat_of_substitutes", FlagCategory.BUSINESS_QUALITY),

            // --- Management: governance, capital allocation ---
            Map.entry("management_credibility_concern", FlagCategory.MANAGEMENT),
            Map.entry("management_capital_allocation_strength", FlagCategory.MANAGEMENT),
            Map.entry("related_party_transaction_flag", FlagCategory.MANAGEMENT),
            Map.entry("auditor_qualification", FlagCategory.MANAGEMENT),

            // --- Risk: regulatory, commodity, concentration, bargaining power ---
            Map.entry("customer_concentration_risk", FlagCategory.RISK),
            Map.entry("regulatory_overhang", FlagCategory.RISK),
            Map.entry("commodity_cost_exposure", FlagCategory.RISK),
            Map.entry("high_supplier_bargaining_power", FlagCategory.RISK),
            Map.entry("high_buyer_bargaining_power", FlagCategory.RISK)
    );

    // flagKey -> base deduction (applied when confidence == "high")
    private static final Map<String, Double> BASE_DEDUCTIONS = Map.ofEntries(
            Map.entry("moat_erosion", 20.0),
            Map.entry("management_credibility_concern", 25.0),
            Map.entry("customer_concentration_risk", 15.0),
            Map.entry("regulatory_overhang", 15.0),
            Map.entry("commodity_cost_exposure", 12.0),
            Map.entry("related_party_transaction_flag", 20.0),
            Map.entry("auditor_qualification", 30.0),
            Map.entry("intense_competitive_rivalry", 12.0),
            Map.entry("high_threat_of_substitutes", 10.0),
            Map.entry("high_supplier_bargaining_power", 10.0),
            Map.entry("high_buyer_bargaining_power", 10.0)
    );

    /**
     * Positive-signal flags add back points instead (still capped at 100).
     */
    private static final Map<String, Double> POSITIVE_ADDITIONS = Map.ofEntries(
            Map.entry("market_share_gain", 10.0),
            Map.entry("durable_pricing_power", 12.0),
            Map.entry("management_capital_allocation_strength", 10.0),
            Map.entry("network_effects_present", 10.0),
            Map.entry("high_switching_costs", 10.0),
            Map.entry("cost_leadership_advantage", 10.0),
            Map.entry("strong_intangible_assets", 8.0),
            Map.entry("large_addressable_market", 8.0)
    );

    public static FlagCategory categoryOf(String flagKey) {
        return FLAG_CATEGORY.get(flagKey);
    }

    /** One row of the flag vocabulary, for the methodology endpoint. */
    public record FlagRule(String flagKey, FlagCategory category, double effectAtHighConfidence) {}

    /**
     * Signed effect of each flag at high confidence (negative = deduction),
     * sorted by category then key. Read-only view of the tables above so the
     * UI can show exactly what the engine applies, with no second copy to drift.
     */
    public static java.util.List<FlagRule> allRules() {
        return FLAG_CATEGORY.entrySet().stream()
                .map(e -> new FlagRule(e.getKey(), e.getValue(),
                        BASE_DEDUCTIONS.containsKey(e.getKey())
                                ? -BASE_DEDUCTIONS.get(e.getKey())
                                : POSITIVE_ADDITIONS.getOrDefault(e.getKey(), 0.0)))
                .sorted(java.util.Comparator.comparing((FlagRule r) -> r.category().ordinal())
                        .thenComparing(FlagRule::flagKey))
                .toList();
    }

    /** Multiplier applied to a flag's effect for each confidence tier. */
    public static java.util.Map<String, Double> confidenceMultipliers() {
        return java.util.Map.of("high", 1.0, "medium", 0.6, "low", 0.3);
    }

    public static double applyFlags(double baseScore, List<QualitativeFlag> flags, FlagCategory category) {
        double score = baseScore;
        for (QualitativeFlag flag : flags) {
            if (!flag.present()) continue;
            if (FLAG_CATEGORY.get(flag.flagKey()) != category) continue; // not this category's concern

            String confidence = flag.confidence() == null ? "" : flag.confidence().toLowerCase();
            double confidenceMultiplier = switch (confidence) {
                case "high" -> 1.0;
                case "medium" -> 0.6;
                case "low" -> 0.3;
                default -> 0.5;
            };
            if (BASE_DEDUCTIONS.containsKey(flag.flagKey())) {
                score -= BASE_DEDUCTIONS.get(flag.flagKey()) * confidenceMultiplier;
            } else if (POSITIVE_ADDITIONS.containsKey(flag.flagKey())) {
                score += POSITIVE_ADDITIONS.get(flag.flagKey()) * confidenceMultiplier;
            }
            // Unknown flagKeys are ignored rather than applied — a novel flag
            // the AI invents that isn't in this table has zero scoring effect
            // until a human adds it here on purpose.
        }
        return BandScorer.clamp(score);
    }
}
