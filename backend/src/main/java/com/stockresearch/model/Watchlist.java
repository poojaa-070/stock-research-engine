package com.stockresearch.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** v2: one row per company the (single) user is tracking. */
@Entity
@Table(name = "watchlist")
@Getter
@Setter
public class Watchlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "added_at")
    private LocalDateTime addedAt;
}
