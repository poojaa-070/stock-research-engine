package com.stockresearch.dto;

import java.util.List;

public record CategoryScoreDto(
        String category,
        double score,
        double dataCompleteness,
        List<EvidenceDto> evidence
) {}
