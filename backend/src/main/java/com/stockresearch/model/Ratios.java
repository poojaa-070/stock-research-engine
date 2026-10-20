package com.stockresearch.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ratios")
@Getter
@Setter
public class Ratios {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    private BigDecimal roe;
    private BigDecimal roce;
    @Column(name = "debt_to_equity")
    private BigDecimal debtToEquity;
    @Column(name = "pe_ratio")
    private BigDecimal peRatio;
    @Column(name = "pb_ratio")
    private BigDecimal pbRatio;
    @Column(name = "ev_ebitda")
    private BigDecimal evEbitda;
    @Column(name = "peg_ratio")
    private BigDecimal pegRatio;
    @Column(name = "dividend_yield")
    private BigDecimal dividendYield;
}
