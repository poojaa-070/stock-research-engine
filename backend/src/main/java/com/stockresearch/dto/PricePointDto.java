package com.stockresearch.dto;

import java.time.LocalDate;

public record PricePointDto(LocalDate date, double close) {}
