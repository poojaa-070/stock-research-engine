package com.stockresearch.scoring;

import com.stockresearch.config.ScoringWeightsConfig;
import com.stockresearch.dto.CategoryScoreResult;
import com.stockresearch.dto.MetricInputs;
import com.stockresearch.dto.QualitativeFlag;
import com.stockresearch.model.ScoringRule;
import com.stockresearch.repository.ScoringRuleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns {config in scoring_rules} + {computed MetricInputs} + {AI-extracted
 * QualitativeFlags} into: 7 category scores, an overall weighted score, a
 * verdict label, and a confidence figure based on data completeness.
 *
 * This class contains ZERO calls to any AI model. It is pure rule
 * application — the non-negotiable requirement from the project brief.
 *
 * v2 changes:
 *  - Sector overrides: a scoring_rules row with a sector replaces the default
 *    row for the same (category, metric_key) for companies in that sector.
 *  - The qualitative flag pool starts from a NEUTRAL baseline (band_scores[0]
 *    of the qualitative_flags rule, seeded as 60), not 100. Negative flags
 *    deduct, positive flags add. v1 started at 100, so "no flags found"
 *    scored as perfect and positive flags were clamped away to nothing.
 *  - If the AI layer didn't run at all (no API key, API error), the
 *    qualitative slot is treated as MISSING data — weights renormalize and
 *    confidence drops — instead of silently contributing a full score.
 *  - A category with no computable data is excluded from the overall score
 *    (v1 counted it as 0/100) and shows 0% data completeness instead.
 */
@Service
public class ScoringEngine {

    public static final String QUALITATIVE_METRIC_KEY = "qualitative_flags";
    static final double DEFAULT_QUALITATIVE_BASELINE = 60.0;

    private static final Map<String, FlagDeductionRules.FlagCategory> CATEGORY_TO_FLAG_CATEGORY = Map.of(
            "business_quality", FlagDeductionRules.FlagCategory.BUSINESS_QUALITY,
            "management", FlagDeductionRules.FlagCategory.MANAGEMENT,
            "risk", FlagDeductionRules.FlagCategory.RISK
    );

    private static final Set<String> POSITIVE_FLAG_KEYS = Set.of(
            "market_share_gain", "durable_pricing_power", "management_capital_allocation_strength",
            "network_effects_present", "high_switching_costs", "cost_leadership_advantage",
            "strong_intangible_assets", "large_addressable_market"
    );

    private final ScoringRuleRepository scoringRuleRepository;
    private final ScoringWeightsConfig weightsConfig;

    public ScoringEngine(ScoringRuleRepository scoringRuleRepository, ScoringWeightsConfig weightsConfig) {
        this.scoringRuleRepository = scoringRuleRepository;
        this.weightsConfig = weightsConfig;
    }

    public Map<String, Double> categoryWeights() {
        return weightsConfig.getCategoryWeights();
    }

    /** v1 signature, kept for compatibility: assumes the AI layer ran and no sector overrides. */
    public Map<String, CategoryScoreResult> scoreAllCategories(MetricInputs inputs, List<QualitativeFlag> flags) {
        return scoreAllCategories(inputs, flags, true, null);
    }

    public Map<String, CategoryScoreResult> scoreAllCategories(MetricInputs inputs, List<QualitativeFlag> flags,
                                                               boolean aiFlagsAvailable, String sector) {
        Map<String, CategoryScoreResult> results = new TreeMap<>();
        for (String category : categoryWeights().keySet()) {
            results.put(category, scoreCategory(category, inputs, flags, aiFlagsAvailable, sector));
        }
        return results;
    }

    /**
     * The rules that apply to one category for one sector: the default rule
     * for each metric_key, replaced by a sector-specific row where one exists
     * (sector match is case-insensitive).
     */
    public List<ScoringRule> effectiveRules(String category, String sector) {
        Map<String, ScoringRule> byMetric = new LinkedHashMap<>();
        List<ScoringRule> all = scoringRuleRepository.findByCategoryAndActiveTrue(category);
        for (ScoringRule rule : all) {
            if (rule.getSector() == null || rule.getSector().isBlank()) {
                byMetric.putIfAbsent(rule.getMetricKey(), rule);
            }
        }
        if (sector != null && !sector.isBlank()) {
            for (ScoringRule rule : all) {
                if (rule.getSector() != null && rule.getSector().equalsIgnoreCase(sector.trim())) {
                    byMetric.put(rule.getMetricKey(), rule);
                }
            }
        }
        return List.copyOf(byMetric.values());
    }

    private CategoryScoreResult scoreCategory(String category, MetricInputs inputs, List<QualitativeFlag> flags,
                                              boolean aiFlagsAvailable, String sector) {
        CategoryScoreResult result = new CategoryScoreResult(category);
        double weightedSum = 0;
        double weightUsed = 0;
        double weightExpected = 0;

        for (ScoringRule rule : effectiveRules(category, sector)) {
            double weight = rule.getWeightWithinCategory().doubleValue();
            weightExpected += weight;

            FlagDeductionRules.FlagCategory flagCategory = CATEGORY_TO_FLAG_CATEGORY.get(category);
            if (QUALITATIVE_METRIC_KEY.equals(rule.getMetricKey()) && flagCategory != null) {
                if (!aiFlagsAvailable) {
                    continue; // AI didn't run: genuinely missing, renormalize around it
                }
                double baseline = rule.getBandScores() == null || rule.getBandScores().isEmpty()
                        ? DEFAULT_QUALITATIVE_BASELINE
                        : rule.getBandScores().get(0).doubleValue();
                double flagScore = FlagDeductionRules.applyFlags(baseline, flags, flagCategory);
                weightedSum += flagScore * weight;
                weightUsed += weight;

                List<QualitativeFlag> relevant = flags.stream()
                        .filter(QualitativeFlag::present)
                        .filter(f -> FlagDeductionRules.categoryOf(f.flagKey()) == flagCategory)
                        .toList();
                if (relevant.isEmpty()) {
                    result.addEvidence("No qualitative flags found in the available text -> neutral "
                                    + Math.round(baseline) + "/100", "AI flag extraction over news/announcements",
                            "neutral");
                }
                relevant.forEach(f -> result.addEvidence(describeFlag(f), f.sourceRef(), inferSentiment(f)));
                continue;
            }

            if (!inputs.has(rule.getMetricKey())) {
                continue; // renormalize around missing metrics, don't guess
            }
            double raw = inputs.get(rule.getMetricKey()).orElseThrow();
            if (!Double.isFinite(raw)) {
                continue; // defensive: never score NaN/Infinity (see BandScorer)
            }

            double bandScore = BandScorer.clamp(
                    BandScorer.score(raw,
                            rule.getBandThresholds().stream().map(BigDecimal::doubleValue).toList(),
                            rule.getBandScores().stream().map(BigDecimal::doubleValue).toList()));

            weightedSum += bandScore * weight;
            weightUsed += weight;

            String sectorNote = rule.getSector() != null && !rule.getSector().isBlank()
                    ? " (" + rule.getSector() + " bands)" : "";
            result.addEvidence(
                    inputs.evidenceNoteFor(rule.getMetricKey()) + " -> scored " + Math.round(bandScore) + "/100" + sectorNote,
                    inputs.sourceFor(rule.getMetricKey()),
                    bandScore >= 65 ? "positive" : bandScore >= 45 ? "neutral" : "negative");
        }

        result.score = weightUsed > 0 ? BandScorer.clamp(weightedSum / weightUsed) : 0;
        result.dataCompleteness = weightExpected > 0 ? weightUsed / weightExpected : 0;
        return result;
    }

    /** Weighted average over categories that had any data at all. */
    public double overallScore(Map<String, CategoryScoreResult> categoryScores) {
        return overallScore(categoryScores, categoryWeights());
    }

    /** Same, with caller-supplied weights (used by the what-if reweighting). */
    public static double overallScore(Map<String, CategoryScoreResult> categoryScores, Map<String, Double> weights) {
        double sum = 0;
        double weightTotal = 0;
        for (var entry : categoryScores.entrySet()) {
            if (entry.getValue().dataCompleteness <= 0) continue;
            double weight = weights.getOrDefault(entry.getKey(), 0.0);
            sum += entry.getValue().score * weight;
            weightTotal += weight;
        }
        return weightTotal > 0 ? BandScorer.clamp(sum / weightTotal) : 0;
    }

    public double overallConfidence(Map<String, CategoryScoreResult> categoryScores) {
        // Weighted average of each category's data completeness. Deliberately
        // NOT a measure of how "good" the numbers are — a company can score
        // low with high confidence (clearly weak) or score decently with low
        // confidence (a lot of gaps padded over by whatever data did exist).
        double sum = 0;
        double weightTotal = 0;
        for (var entry : categoryScores.entrySet()) {
            double weight = categoryWeights().getOrDefault(entry.getKey(), 0.0);
            sum += entry.getValue().dataCompleteness * weight;
            weightTotal += weight;
        }
        return weightTotal > 0 ? sum / weightTotal : 0;
    }

    public String verdictFor(double overallScore) {
        if (overallScore >= 80) return "🟢 Strong Candidate";
        if (overallScore >= 65) return "🟢 Worth Researching";
        if (overallScore >= 50) return "🟡 Neutral–Watch";
        if (overallScore >= 35) return "🟠 High Risk";
        return "🔴 Weak Fundamentals";
    }

    /** Verdict bands as data, for the methodology page: lower bound -> label. */
    public static List<Map.Entry<Double, String>> verdictBands() {
        return List.of(
                Map.entry(80.0, "🟢 Strong Candidate"),
                Map.entry(65.0, "🟢 Worth Researching"),
                Map.entry(50.0, "🟡 Neutral–Watch"),
                Map.entry(35.0, "🟠 High Risk"),
                Map.entry(0.0, "🔴 Weak Fundamentals"));
    }

    public static String describeFlagKey(String flagKey) {
        return switch (flagKey) {
            case "moat_erosion" -> "Signals of competitive moat erosion detected";
            case "management_credibility_concern" -> "Management credibility concern flagged";
            case "customer_concentration_risk" -> "Customer/revenue concentration risk flagged";
            case "regulatory_overhang" -> "Regulatory overhang flagged";
            case "commodity_cost_exposure" -> "Commodity/input cost exposure flagged";
            case "related_party_transaction_flag" -> "Related-party transaction concern flagged";
            case "auditor_qualification" -> "Auditor qualification/going-concern note flagged";
            case "market_share_gain" -> "Evidence of market share gains";
            case "durable_pricing_power" -> "Evidence of durable pricing power";
            case "management_capital_allocation_strength" -> "Strong capital allocation track record noted";
            case "network_effects_present" -> "Network-effect dynamics noted (product gets more valuable as usage grows)";
            case "high_switching_costs" -> "High customer switching costs noted";
            case "cost_leadership_advantage" -> "Cost leadership / scale advantage noted";
            case "strong_intangible_assets" -> "Strong brand, patent, or licensing moat noted";
            case "large_addressable_market" -> "Large total addressable market noted";
            case "intense_competitive_rivalry" -> "Intense competitive rivalry flagged";
            case "high_threat_of_substitutes" -> "High threat of substitute products/services flagged";
            case "high_supplier_bargaining_power" -> "Suppliers hold significant bargaining power";
            case "high_buyer_bargaining_power" -> "Customers/buyers hold significant bargaining power";
            default -> flagKey + " flagged";
        };
    }

    private String describeFlag(QualitativeFlag f) {
        return describeFlagKey(f.flagKey()) + " (confidence: " + f.confidence() + "): \"" + f.evidenceQuote() + "\"";
    }

    private String inferSentiment(QualitativeFlag f) {
        return POSITIVE_FLAG_KEYS.contains(f.flagKey()) ? "positive" : "negative";
    }
}
