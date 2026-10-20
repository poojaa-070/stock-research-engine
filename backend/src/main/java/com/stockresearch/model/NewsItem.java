package com.stockresearch.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "news_items")
@Getter
@Setter
public class NewsItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(nullable = false)
    private String headline;

    private String url;
    private String source;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    private String sentiment; // "positive" | "neutral" | "negative"
}
