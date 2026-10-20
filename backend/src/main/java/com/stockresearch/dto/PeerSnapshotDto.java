package com.stockresearch.dto;

/**
 * One sector peer's headline numbers, for the "competitive position" mini
 * table. overallScore is null if that peer hasn't had a report generated
 * yet -- shown as "not yet analyzed" rather than a guessed number.
 * companyId is null for a peer that only came from a live Screener
 * peer-comparison scrape and isn't (yet) a row in our own `companies`
 * table -- such a peer isn't tappable into a full report, only shown for
 * its live headline numbers, distinguished in the UI via `isLive`.
 */
public record PeerSnapshotDto(
        Long companyId,
        String name,
        String nseSymbol,
        Double peRatio,
        Double rocePercent,
        Double overallScore,
        Double cmp,
        boolean isLive
) {}
