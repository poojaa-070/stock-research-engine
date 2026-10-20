package com.stockresearch.dto;

import java.time.LocalDate;

/** One annual period for the financial-history chart. Values in ₹ crore as scraped. */
public record FinancialPointDto(
        LocalDate periodEndDate,
        Double revenue,
        Double netProfit,
        Double operatingProfit,
        Double eps,
        Double operatingMarginPercent,
        Double netMarginPercent
) {}
