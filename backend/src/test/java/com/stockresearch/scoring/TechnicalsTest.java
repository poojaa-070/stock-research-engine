package com.stockresearch.scoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TechnicalsTest {

    @Test
    void wilderRsiMatchesTextbookSample() {
        // Classic 15-close sample (Wilder / StockCharts): RSI(14) ~ 70.5.
        List<Double> closes = List.of(44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42,
                45.84, 46.08, 45.89, 46.03, 45.61, 46.28, 46.28);
        assertEquals(70.5, Technicals.rsiWilder(closes, 14).getAsDouble(), 0.2);
    }

    @Test
    void rsiNeedsPeriodPlusOneCloses() {
        assertTrue(Technicals.rsiWilder(List.of(1.0, 2.0, 3.0), 14).isEmpty());
    }

    @Test
    void rsiOfOnlyRisingPricesIs100AndFlatIs50() {
        List<Double> rising = new java.util.ArrayList<>();
        List<Double> flat = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rising.add(100.0 + i);
            flat.add(100.0);
        }
        assertEquals(100.0, Technicals.rsiWilder(rising, 14).getAsDouble(), 1e-9);
        assertEquals(50.0, Technicals.rsiWilder(flat, 14).getAsDouble(), 1e-9);
    }

    @Test
    void smaAveragesTheLastNCloses() {
        assertEquals(4.0, Technicals.sma(List.of(1.0, 2.0, 3.0, 4.0, 5.0), 3).getAsDouble(), 1e-9);
        assertTrue(Technicals.sma(List.of(1.0), 3).isEmpty());
    }
}
