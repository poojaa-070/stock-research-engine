package com.stockresearch.service;

import com.stockresearch.model.Prices;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceChangeTest {

    private static List<Prices> dailyDesc(int days, double start, double end) {
        List<Prices> asc = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 9, 1).minusDays(days - 1);
        for (int i = 0; i < days; i++) {
            Prices p = new Prices();
            p.setTradeDate(d.plusDays(i));
            p.setClose(BigDecimal.valueOf(start + (end - start) * i / (days - 1)));
            asc.add(p);
        }
        return asc.reversed();
    }

    @Test
    void usesTheCloseFromOneYearAgo() {
        List<Prices> desc = dailyDesc(400, 100, 140);
        Double change = ReportService.priceChange1y(desc, 140.0);
        assertNotNull(change);
        // close exactly 365 days before the latest row
        double yearAgo = desc.get(365).getClose().doubleValue();
        assertEquals((140 - yearAgo) / yearAgo * 100, change, 1e-6);
    }

    @Test
    void returnsNullWithLessThanAYearOfHistory() {
        // v1 reported ~10 months of history as a "1y" change.
        assertNull(ReportService.priceChange1y(dailyDesc(300, 100, 120), 120.0));
    }
}
