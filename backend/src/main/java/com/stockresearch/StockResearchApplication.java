package com.stockresearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // v2: optional watchlist refresh (off unless app.watchlist.refresh-cron is set)
public class StockResearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockResearchApplication.class, args);
    }
}
