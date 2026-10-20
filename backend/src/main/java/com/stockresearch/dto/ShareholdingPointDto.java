package com.stockresearch.dto;

import java.time.LocalDate;

public record ShareholdingPointDto(
        LocalDate periodEndDate,
        Double promoterPct,
        Double fiiPct,
        Double diiPct,
        Double publicPct
) {}
