package com.stockresearch.scoring;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Price-series indicators shared by the Momentum score (MetricCalculator) and
 * the raw Technical Setup panel (ReportService). v1 had two separate copies of
 * the moving-average and RSI code, which could drift apart so the panel and
 * the score disagreed; both now call this one implementation.
 *
 * All inputs are closing prices ordered OLDEST FIRST.
 */
public final class Technicals {

    private Technicals() {}

    /** Simple moving average of the last {@code period} closes, if there are enough. */
    public static OptionalDouble sma(List<Double> closesAsc, int period) {
        if (period <= 0 || closesAsc.size() < period) return OptionalDouble.empty();
        double sum = 0;
        for (int i = closesAsc.size() - period; i < closesAsc.size(); i++) {
            sum += closesAsc.get(i);
        }
        return OptionalDouble.of(sum / period);
    }

    /**
     * RSI using Wilder's smoothing — the standard definition charting platforms
     * use. v1 used a plain average over only the last 14 changes, which reads
     * noticeably differently from what a user sees on their broker's chart.
     *
     * Seeds the average gain/loss from the first {@code period} changes, then
     * smooths across the rest of the available history, so a longer series
     * gives a more settled value. Needs at least period + 1 closes.
     */
    public static OptionalDouble rsiWilder(List<Double> closesAsc, int period) {
        if (period <= 0 || closesAsc.size() < period + 1) return OptionalDouble.empty();

        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double delta = closesAsc.get(i) - closesAsc.get(i - 1);
            if (delta >= 0) gain += delta; else loss -= delta;
        }
        double avgGain = gain / period;
        double avgLoss = loss / period;

        for (int i = period + 1; i < closesAsc.size(); i++) {
            double delta = closesAsc.get(i) - closesAsc.get(i - 1);
            double g = delta > 0 ? delta : 0;
            double l = delta < 0 ? -delta : 0;
            avgGain = (avgGain * (period - 1) + g) / period;
            avgLoss = (avgLoss * (period - 1) + l) / period;
        }

        if (avgLoss == 0) return OptionalDouble.of(avgGain == 0 ? 50.0 : 100.0);
        double rs = avgGain / avgLoss;
        return OptionalDouble.of(100 - (100 / (1 + rs)));
    }
}
