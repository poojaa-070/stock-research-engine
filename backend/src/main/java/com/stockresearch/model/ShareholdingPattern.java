package com.stockresearch.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "shareholding_pattern")
@Getter
@Setter
public class ShareholdingPattern {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    @Column(name = "promoter_pct")
    private BigDecimal promoterPct;
    @Column(name = "fii_pct")
    private BigDecimal fiiPct;
    @Column(name = "dii_pct")
    private BigDecimal diiPct;
    @Column(name = "public_pct")
    private BigDecimal publicPct;
}
