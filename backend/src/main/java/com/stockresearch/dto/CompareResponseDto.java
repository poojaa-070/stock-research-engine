package com.stockresearch.dto;

import java.util.List;

public record CompareResponseDto(
        CompanySummaryDto companyA,
        CompanySummaryDto companyB,
        double overallScoreA,
        double overallScoreB,
        String overallWinner,
        List<CompareRowDto> rows
) {}
