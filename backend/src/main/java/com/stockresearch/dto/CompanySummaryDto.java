package com.stockresearch.dto;

public record CompanySummaryDto(Long id, String name, String nseSymbol, String bseCode, String sector, String industry) {}
