package com.stockresearch.scoring;

import com.stockresearch.dto.CategoryScoreResult;
import com.stockresearch.dto.MetricInputs;
import com.stockresearch.dto.QualitativeFlag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class ScoringEngineTest {

    private final ScoringEngine engine = TestRules.engine();

    @Test
    void qualitativePoolStartsNeutralAndPositiveFlagsAdd() {
        double none = engine.scoreAllCategories(new MetricInputs(), List.of(), true, null).get("business_quality").score;
        double positive = engine.scoreAllCategories(new MetricInputs(),
                List.of(new QualitativeFlag("durable_pricing_power", true, "high", "quote", "src")), true, null)
                .get("business_quality").score;
        assertEquals(60.0, none, 1e-9);
        assertEquals(72.0, positive, 1e-9); // +12 at high confidence
    }

    @Test
    void flagsOnlyAffectTheirOwnCategory() {
        var flags = List.of(new QualitativeFlag("regulatory_overhang", true, "high", "q", "s"));
        var scores = engine.scoreAllCategories(new MetricInputs(), flags, true, null);
        assertEquals(45.0, scores.get("risk").score, 1e-9);            // 60 - 15
        assertEquals(60.0, scores.get("business_quality").score, 1e-9); // untouched
    }

    @Test
    void aiUnavailableMeansMissingDataNotAFreeScore() {
        CategoryScoreResult bq = engine.scoreAllCategories(new MetricInputs(), List.of(), false, null).get("business_quality");
        assertEquals(0.0, bq.dataCompleteness, 1e-9);
    }

    @Test
    void emptyCategoriesAreExcludedFromOverall() {
        Map<String, CategoryScoreResult> m = new TreeMap<>();
        CategoryScoreResult growth = new CategoryScoreResult("growth");
        growth.score = 80;
        growth.dataCompleteness = 1;
        CategoryScoreResult risk = new CategoryScoreResult("risk");
        risk.score = 0;
        risk.dataCompleteness = 0;
        m.put("growth", growth);
        m.put("risk", risk);
        assertEquals(80.0, engine.overallScore(m), 1e-9);
    }

    @Test
    void sectorOverrideReplacesDefaultRule() {
        MetricInputs in = new MetricInputs();
        in.put("roce_latest", 25, "ROCE 25%", "test");
        double generic = engine.scoreAllCategories(in, List.of(), false, null).get("business_quality").score;
        double it = engine.scoreAllCategories(in, List.of(), false, "it").get("business_quality").score;
        assertEquals(100.0, generic, 1e-9); // default bands: 25%+ -> 100
        // IT bands: score at threshold t[i] is scores[i+1], so 25 sits halfway
        // between 20 (-> 60) and 30 (-> 80).
        assertEquals(70.0, it, 1e-9);
    }

    @Test
    void stableMarginsNowBeatVolatileOnes() {
        MetricInputs stable = new MetricInputs();
        stable.put("margin_stability", 0.02, "", "");
        MetricInputs volatile_ = new MetricInputs();
        volatile_.put("margin_stability", 0.20, "", "");
        double s = engine.scoreAllCategories(stable, List.of(), false, null).get("business_quality").score;
        double v = engine.scoreAllCategories(volatile_, List.of(), false, null).get("business_quality").score;
        assertTrue(s > v, "stable " + s + " should beat volatile " + v);
    }

    @Test
    void verdictBandsAreAPureLookup() {
        assertEquals("🟢 Strong Candidate", engine.verdictFor(80));
        assertEquals("🟢 Worth Researching", engine.verdictFor(79.9));
        assertEquals("🟡 Neutral–Watch", engine.verdictFor(50));
        assertEquals("🟠 High Risk", engine.verdictFor(35));
        assertEquals("🔴 Weak Fundamentals", engine.verdictFor(34.9));
    }

    @Test
    void nullFlagConfidenceDoesNotThrow() {
        double s = FlagDeductionRules.applyFlags(60,
                List.of(new QualitativeFlag("moat_erosion", true, null, "", "")),
                FlagDeductionRules.FlagCategory.BUSINESS_QUALITY);
        assertEquals(50.0, s, 1e-9); // 20 * 0.5 default multiplier
    }
}
