package com.stockresearch.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Read-only view of the scraper's bookkeeping table, used to tell the user how
 * fresh a report's underlying data is. raw_payload (JSONB) is deliberately not
 * mapped: it can hold a large HTML snapshot and the API never needs it.
 */
@Entity
@Table(name = "scrape_metadata")
@Getter
@Setter
public class ScrapeMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    private String source;

    @Column(name = "last_scraped_at")
    private LocalDateTime lastScrapedAt;

    private String status;
}
