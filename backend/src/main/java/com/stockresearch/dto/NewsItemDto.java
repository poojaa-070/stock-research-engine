package com.stockresearch.dto;

import java.time.LocalDateTime;

public record NewsItemDto(String headline, String source, LocalDateTime publishedAt, String url) {}
