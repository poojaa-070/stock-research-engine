package com.stockresearch.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record WatchlistItemDto(
        CompanySummaryDto company,
        String note,
        LocalDateTime addedAt,
        Double latestScore,       // null if never analysed
        String latestVerdict,
        LocalDateTime lastReportAt,
        Double scoreChange,       // latest minus previous report, null if fewer than two
        Double lastClose,         // latest scraped close, not live
        LocalDate lastCloseDate
) {}
