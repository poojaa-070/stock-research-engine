package com.stockresearch.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_reports")
@Getter
@Setter
public class AnalysisReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "overall_score")
    private BigDecimal overallScore;
    @Column(name = "financial_health_score")
    private BigDecimal financialHealthScore;
    @Column(name = "business_quality_score")
    private BigDecimal businessQualityScore;
    @Column(name = "growth_score")
    private BigDecimal growthScore;
    @Column(name = "valuation_score")
    private BigDecimal valuationScore;
    @Column(name = "management_score")
    private BigDecimal managementScore;
    @Column(name = "risk_score")
    private BigDecimal riskScore;
    @Column(name = "momentum_score")
    private BigDecimal momentumScore;

    private String verdict;
    private BigDecimal confidence;
}
