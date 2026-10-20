package com.stockresearch.dto;

import java.time.LocalDate;

/**
 * Latest reported quarter, plus YoY growth vs the same quarter a year
 * earlier (matched by date, not just "the previous row" -- a quarter's
 * natural comparison is the same quarter last year, not last quarter,
 * because of seasonality).
 */
public record QuarterlyResultDto(
        LocalDate periodEndDate,
        Double revenue,
        Double netProfit,
        Double operatingProfit,
        Double operatingMarginPercent,
        Double revenueYoyPercent,
        Double netProfitYoyPercent,
        LocalDate comparedToPeriodEndDate // which prior quarter the YoY is against, null if unavailable
) {}
