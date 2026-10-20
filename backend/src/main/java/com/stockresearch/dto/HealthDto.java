package com.stockresearch.dto;

import java.time.LocalDateTime;

/** status is "ok" when the database is up; scraper/AI are optional and reported separately. */
public record HealthDto(String status, String database, String scraper, String ai, LocalDateTime checkedAt) {}
