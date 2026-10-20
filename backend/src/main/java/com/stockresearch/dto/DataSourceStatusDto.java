package com.stockresearch.dto;

import java.time.LocalDateTime;

/** How fresh one data source is for a company: "screener" or "nse". */
public record DataSourceStatusDto(String source, LocalDateTime lastScrapedAt, String status) {}
