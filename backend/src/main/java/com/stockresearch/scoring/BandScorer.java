package com.stockresearch.scoring;

import java.math.BigDecimal;
import java.util.List;

/**
 * One reusable primitive for turning a raw metric value into a 0-100 score,
 * via piecewise-linear interpolation between configured bands.
 *
 * A rule has N ascending thresholds and N+1 scores:
 *   thresholds = [t1, t2, ..., tN]
 *   scores     = [s0, s1, ..., sN]   (s0 = score when value <= t1's regime)
 *
 * value < t1            -> s0 (or linear approach toward s0/s1, see below)
 * t1 <= value <= t2      -> interpolate between s1 and s2 boundary points
 * value > tN             -> sN
 *
 * "higherIsBetter" doesn't change the math here — it's already baked into the
 * shape of the scores array by whoever authored the scoring_rules row
 * (see docs/SCORING_FORMULA.md section 1). This class is direction-agnostic
 * on purpose: it just interpolates the curve it's given.
 *
 * v2 hardening: non-finite values and non-ascending thresholds are rejected
 * instead of silently producing a wrong score (see the two checks below).
 */
public final class BandScorer {

    private BandScorer() {}

    public static double score(double value, List<Double> thresholds, List<Double> scores) {
        if (!Double.isFinite(value)) {
            // v1 bug: a NaN (e.g. CAGR of a figure that turned negative) failed
            // every comparison below and fell through to the LAST score — the
            // best one. Callers must never pass a non-finite value.
            throw new IllegalArgumentException("Cannot band-score a non-finite value: " + value);
        }
        if (scores.isEmpty()) {
            throw new IllegalArgumentException("A scoring rule must define at least one score");
        }
        if (scores.size() != thresholds.size() + 1) {
            throw new IllegalArgumentException(
                "band_scores must have exactly one more element than band_thresholds " +
                "(got " + scores.size() + " scores for " + thresholds.size() + " thresholds)");
        }
        if (thresholds.isEmpty()) {
            // Degenerate rule (used for flag-derived metrics resolved elsewhere) — flat score.
            return scores.get(0);
        }
        for (int i = 1; i < thresholds.size(); i++) {
            if (thresholds.get(i) < thresholds.get(i - 1)) {
                // v1 seeded margin_stability with descending thresholds, which
                // silently inverted its scores. Fail loudly instead.
                throw new IllegalArgumentException("band_thresholds must be ascending, got " + thresholds);
            }
        }

        // Below the first threshold: clamp to the first score.
        if (value <= thresholds.get(0)) {
            return scores.get(0);
        }
        // Above the last threshold: clamp to the last score.
        int last = thresholds.size() - 1;
        if (value >= thresholds.get(last)) {
            return scores.get(scores.size() - 1);
        }
        // Find the bracketing pair and interpolate linearly.
        for (int i = 0; i < thresholds.size() - 1; i++) {
            double lo = thresholds.get(i);
            double hi = thresholds.get(i + 1);
            if (value >= lo && value <= hi) {
                double loScore = scores.get(i + 1);
                double hiScore = scores.get(i + 2);
                double fraction = hi == lo ? 0 : (value - lo) / (hi - lo);
                return loScore + fraction * (hiScore - loScore);
            }
        }
        // Unreachable given the clamps and ascending check above.
        return scores.get(scores.size() - 1);
    }

    public static double score(double value, BigDecimal[] thresholds, BigDecimal[] scores) {
        return score(value, toDoubleList(thresholds), toDoubleList(scores));
    }

    private static List<Double> toDoubleList(BigDecimal[] arr) {
        return java.util.Arrays.stream(arr).map(BigDecimal::doubleValue).toList();
    }

    /** Clamp any final score into [0, 100] — defensive, in case config authors slip up. */
    public static double clamp(double raw) {
        return Math.max(0.0, Math.min(100.0, raw));
    }
}
