package com.stockresearch.dto;

/** Intraday detail from NSE's live quote; any field may be null if NSE omitted it. */
public record LiveQuoteDto(
        Double change,
        Double percentChange,
        Double previousClose,
        Double dayHigh,
        Double dayLow,
        String marketState
) {}
