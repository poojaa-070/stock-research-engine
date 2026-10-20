package com.stockresearch.dto;

/**
 * Raw technical numbers -- where the price sits right now vs its own
 * recent range and trend, distinct from the 0-100 Momentum category score.
 * All fields null if there isn't enough price history yet.
 */
public record TechnicalSnapshotDto(
        Double currentPrice,
        Double week52High,
        Double week52Low,
        Double percentFrom52wHigh,   // negative = below the high, e.g. -12.4 means 12.4% off the high
        Double dma50,
        Double dma200,
        Double rsi14
) {
    public TechnicalSnapshotDto withCurrentPrice(double livePrice) {
        double newPercentFromHigh = week52High != null && week52High != 0
                ? ((livePrice - week52High) / week52High) * 100
                : percentFrom52wHigh;
        return new TechnicalSnapshotDto(livePrice, week52High, week52Low, newPercentFromHigh, dma50, dma200, rsi14);
    }
}
