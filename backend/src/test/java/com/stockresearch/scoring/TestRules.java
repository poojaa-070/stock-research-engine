package com.stockresearch.scoring;

import com.stockresearch.config.ScoringWeightsConfig;
import com.stockresearch.model.ScoringRule;
import com.stockresearch.repository.ScoringRuleRepository;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;

/** Test fixtures: the seeded default rules, without a database. */
final class TestRules {
    private TestRules() {}

    static ScoringRule rule(String category, String metric, String sector, double[] thresholds, double[] scores, double weight) {
        ScoringRule r = new ScoringRule();
        r.setCategory(category);
        r.setMetricKey(metric);
        r.setSector(sector);
        r.setBandThresholds(Arrays.stream(thresholds).mapToObj(BigDecimal::valueOf).toList());
        r.setBandScores(Arrays.stream(scores).mapToObj(BigDecimal::valueOf).toList());
        r.setWeightWithinCategory(BigDecimal.valueOf(weight));
        r.setActive(true);
        return r;
    }

    static List<ScoringRule> defaults() {
        return List.of(
                rule("financial_health", "debt_to_equity", null, new double[]{0.5, 1, 2, 3}, new double[]{100, 85, 60, 35, 10}, 0.20),
                rule("financial_health", "cash_conversion", null, new double[]{0.5, 0.8, 1, 1.2, 1.5}, new double[]{10, 40, 55, 70, 90, 100}, 0.15),
                rule("business_quality", "roce_latest", null, new double[]{5, 10, 15, 20, 25}, new double[]{15, 40, 65, 85, 100, 100}, 0.30),
                rule("business_quality", "roce_latest", "IT", new double[]{10, 20, 30, 40, 50}, new double[]{15, 40, 60, 80, 95, 100}, 0.30),
                rule("business_quality", "margin_stability", null, new double[]{0.03, 0.08, 0.15}, new double[]{90, 90, 60, 30}, 0.15),
                rule("business_quality", "qualitative_flags", null, new double[]{}, new double[]{60}, 0.40),
                rule("growth", "revenue_cagr_3y", null, new double[]{0, 5, 10, 15, 20}, new double[]{20, 45, 65, 85, 100, 100}, 0.30),
                rule("growth", "profit_cagr_3y", null, new double[]{0, 5, 10, 15, 20}, new double[]{15, 40, 65, 85, 100, 100}, 0.30),
                rule("risk", "debt_to_equity", null, new double[]{0.5, 1, 2, 3}, new double[]{100, 85, 60, 35, 10}, 0.30),
                rule("risk", "qualitative_flags", null, new double[]{}, new double[]{60}, 0.45));
    }

    static ScoringRuleRepository repo(List<ScoringRule> rules) {
        return (ScoringRuleRepository) Proxy.newProxyInstance(TestRules.class.getClassLoader(),
                new Class<?>[]{ScoringRuleRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findByCategoryAndActiveTrue" -> rules.stream().filter(r -> r.getCategory().equals(args[0])).toList();
                    case "findByActiveTrue" -> rules;
                    case "toString" -> "FakeScoringRuleRepository";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static ScoringEngine engine() {
        ScoringWeightsConfig cfg = new ScoringWeightsConfig();
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("financial_health", 0.20);
        w.put("business_quality", 0.20);
        w.put("growth", 0.15);
        w.put("valuation", 0.20);
        w.put("management", 0.10);
        w.put("risk", 0.10);
        w.put("momentum", 0.05);
        cfg.setCategoryWeights(w);
        return new ScoringEngine(repo(defaults()), cfg);
    }
}
