package com.stockresearch.controller;

import com.stockresearch.dto.MethodologyDto;
import com.stockresearch.model.ScoringRule;
import com.stockresearch.repository.ScoringRuleRepository;
import com.stockresearch.scoring.FlagDeductionRules;
import com.stockresearch.scoring.ScoringEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * v2: GET /api/scoring/methodology — the exact rubric the engine applies,
 * read from the same tables and code it uses, so the Methodology page can
 * never drift from the real scoring.
 */
@RestController
@RequestMapping("/api/scoring")
public class ScoringController {

    private final ScoringRuleRepository scoringRuleRepository;
    private final ScoringEngine scoringEngine;
    private final boolean skipLeverageForFinancials;

    public ScoringController(ScoringRuleRepository scoringRuleRepository, ScoringEngine scoringEngine,
                             @Value("${scoring.skip-leverage-for-financials:true}") boolean skipLeverageForFinancials) {
        this.scoringRuleRepository = scoringRuleRepository;
        this.scoringEngine = scoringEngine;
        this.skipLeverageForFinancials = skipLeverageForFinancials;
    }

    @GetMapping("/methodology")
    public MethodologyDto methodology() {
        List<MethodologyDto.Rule> rules = scoringRuleRepository.findByActiveTrue().stream()
                .sorted(Comparator.comparing(ScoringRule::getCategory)
                        .thenComparing(ScoringRule::getMetricKey)
                        .thenComparing(r -> r.getSector() == null ? "" : r.getSector()))
                .map(r -> new MethodologyDto.Rule(r.getCategory(), r.getMetricKey(), r.getSector(),
                        toDoubles(r.getBandThresholds()), toDoubles(r.getBandScores()),
                        r.getWeightWithinCategory() == null ? 0 : r.getWeightWithinCategory().doubleValue(),
                        r.isHigherIsBetter()))
                .toList();

        List<MethodologyDto.Flag> flags = FlagDeductionRules.allRules().stream()
                .map(f -> new MethodologyDto.Flag(f.flagKey(), f.category().name().toLowerCase(),
                        ScoringEngine.describeFlagKey(f.flagKey()), f.effectAtHighConfidence()))
                .toList();

        List<MethodologyDto.VerdictBand> bands = ScoringEngine.verdictBands().stream()
                .map(e -> new MethodologyDto.VerdictBand(e.getKey(), e.getValue()))
                .toList();

        return new MethodologyDto(scoringEngine.categoryWeights(), rules, flags,
                FlagDeductionRules.confidenceMultipliers(), bands, skipLeverageForFinancials);
    }

    private static List<Double> toDoubles(List<BigDecimal> values) {
        return values == null ? List.of() : values.stream().map(BigDecimal::doubleValue).toList();
    }
}
