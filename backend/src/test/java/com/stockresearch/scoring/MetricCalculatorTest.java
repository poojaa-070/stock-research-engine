package com.stockresearch.scoring;

import com.stockresearch.dto.MetricInputs;
import com.stockresearch.model.Company;
import com.stockresearch.model.Financials;
import com.stockresearch.model.Ratios;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MetricCalculatorTest {

    private final MetricCalculator calc = new MetricCalculator(true);

    private static Company company(String sector, String industry) {
        Company c = new Company();
        c.setId(1L);
        c.setName("TestCo");
        c.setSector(sector);
        c.setIndustry(industry);
        return c;
    }

    private static Financials annual(int year, Double revenue, Double profit, Double eps) {
        Financials f = new Financials();
        f.setPeriodType("annual");
        f.setPeriodEndDate(LocalDate.of(year, 3, 31));
        f.setRevenue(revenue == null ? null : BigDecimal.valueOf(revenue));
        f.setNetProfit(profit == null ? null : BigDecimal.valueOf(profit));
        f.setEps(eps == null ? null : BigDecimal.valueOf(eps));
        return f;
    }

    @Test
    void profitTurningIntoALossIsACollapseNotNaN() {
        MetricInputs in = calc.compute(company("Industrials", "Machinery"),
                List.of(annual(2023, 1000.0, 100.0, 10.0), annual(2026, 900.0, -50.0, -5.0)),
                List.of(), List.of(), List.of(), List.of());
        assertEquals(-100.0, in.get("profit_cagr_3y").orElseThrow(), 1e-9);
        assertEquals(-100.0, in.get("eps_cagr_3y").orElseThrow(), 1e-9);
        assertTrue(in.evidenceNoteFor("profit_cagr_3y").contains("turned negative"));
    }

    @Test
    void growthFromALossIsLeftAbsent() {
        MetricInputs in = calc.compute(company("Industrials", "Machinery"),
                List.of(annual(2023, 1000.0, -100.0, -1.0), annual(2026, 1200.0, 80.0, 1.0)),
                List.of(), List.of(), List.of(), List.of());
        assertFalse(in.has("profit_cagr_3y"));
        assertTrue(in.has("revenue_cagr_3y"));
    }

    @Test
    void cagrUsesAThreeYearWindowWhenLongerHistoryExists() {
        List<Financials> fin = new ArrayList<>();
        for (int y = 2019; y <= 2026; y++) {
            fin.add(annual(y, 100 * Math.pow(1.10, y - 2019), null, null));
        }
        MetricInputs in = calc.compute(company("IT", "IT Services"), fin, List.of(), List.of(), List.of(), List.of());
        assertEquals(10.0, in.get("revenue_cagr_3y").orElseThrow(), 0.05);
        assertTrue(in.evidenceNoteFor("revenue_cagr_3y").contains("over 3 yr(s)"),
                in.evidenceNoteFor("revenue_cagr_3y"));
    }

    @Test
    void shortHistoryIsLabelledWithItsRealSpan() {
        MetricInputs in = calc.compute(company("IT", "IT Services"),
                List.of(annual(2025, 100.0, null, null), annual(2026, 110.0, null, null)),
                List.of(), List.of(), List.of(), List.of());
        assertTrue(in.evidenceNoteFor("revenue_cagr_3y").contains("over 1 yr(s)"));
    }

    @Test
    void banksSkipLeverageMetrics() {
        Ratios r = new Ratios();
        r.setPeriodEndDate(LocalDate.of(2026, 3, 31));
        r.setDebtToEquity(BigDecimal.valueOf(6.5));
        MetricInputs bank = calc.compute(company("Financial Services", "Private Bank"),
                List.of(), List.of(r), List.of(), List.of(), List.of());
        MetricInputs it = calc.compute(company("IT", "IT Services"),
                List.of(), List.of(r), List.of(), List.of(), List.of());
        assertFalse(bank.has("debt_to_equity"));
        assertTrue(it.has("debt_to_equity"));
        // and the switch restores v1 behaviour
        assertTrue(new MetricCalculator(false).compute(company("Financial Services", "Private Bank"),
                List.of(), List.of(r), List.of(), List.of(), List.of()).has("debt_to_equity"));
    }

    @Test
    void cashConversionIgnoresLossMakingYears() {
        Financials f = annual(2026, 100.0, -10.0, null);
        f.setOperatingCashFlow(BigDecimal.valueOf(20));
        MetricInputs in = calc.compute(company("IT", "x"), List.of(f), List.of(), List.of(), List.of(), List.of());
        assertFalse(in.has("cash_conversion"));
    }

    @Test
    void financialInstitutionDetection() {
        assertTrue(MetricCalculator.isFinancialInstitution(company("Financial Services", "Private Bank")));
        assertTrue(MetricCalculator.isFinancialInstitution(company("", "Housing Finance")));
        assertFalse(MetricCalculator.isFinancialInstitution(company("IT", "IT Services")));
        assertFalse(MetricCalculator.isFinancialInstitution(company(null, null)));
    }
}
