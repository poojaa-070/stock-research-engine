package com.stockresearch.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "financials")
@Getter
@Setter
public class Financials {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "period_type", nullable = false)
    private String periodType; // "annual" | "quarterly"

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    private BigDecimal revenue;
    @Column(name = "net_profit")
    private BigDecimal netProfit;
    @Column(name = "operating_profit")
    private BigDecimal operatingProfit;
    private BigDecimal eps;
    @Column(name = "total_debt")
    private BigDecimal totalDebt;
    @Column(name = "free_cash_flow")
    private BigDecimal freeCashFlow;
    @Column(name = "operating_cash_flow")
    private BigDecimal operatingCashFlow;
    @Column(name = "interest_expense")
    private BigDecimal interestExpense;
    @Column(name = "current_assets")
    private BigDecimal currentAssets;
    @Column(name = "current_liabilities")
    private BigDecimal currentLiabilities;
}
