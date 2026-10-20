package com.stockresearch.scoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BandScorerTest {

    private static final List<Double> T = List.of(0.0, 5.0, 10.0);
    private static final List<Double> S = List.of(10.0, 40.0, 70.0, 100.0);

    @Test
    void clampsBelowFirstAndAboveLastThreshold() {
        assertEquals(10.0, BandScorer.score(-3, T, S), 1e-9);
        assertEquals(100.0, BandScorer.score(42, T, S), 1e-9);
    }

    @Test
    void interpolatesLinearlyBetweenThresholds() {
        assertEquals(55.0, BandScorer.score(2.5, T, S), 1e-9);
        assertEquals(85.0, BandScorer.score(7.5, T, S), 1e-9);
    }

    @Test
    void rejectsNonFiniteValues() {
        // v1 scored NaN as the best band.
        assertThrows(IllegalArgumentException.class, () -> BandScorer.score(Double.NaN, T, S));
        assertThrows(IllegalArgumentException.class, () -> BandScorer.score(Double.POSITIVE_INFINITY, T, S));
    }

    @Test
    void rejectsDescendingThresholds() {
        // v1's margin_stability seed had these reversed.
        assertThrows(IllegalArgumentException.class,
                () -> BandScorer.score(0.05, List.of(0.15, 0.08, 0.03), List.of(30.0, 60.0, 90.0, 90.0)));
    }

    @Test
    void rejectsMismatchedScoreCount() {
        assertThrows(IllegalArgumentException.class, () -> BandScorer.score(1, T, List.of(1.0, 2.0)));
    }

    @Test
    void emptyThresholdsReturnFlatScore() {
        assertEquals(60.0, BandScorer.score(123, List.of(), List.of(60.0)), 1e-9);
    }
}
