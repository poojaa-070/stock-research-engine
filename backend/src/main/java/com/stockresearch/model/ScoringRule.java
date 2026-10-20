package com.stockresearch.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "scoring_rules")
@Getter
@Setter
public class ScoringRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String category;

    @Column(name = "metric_key")
    private String metricKey;

    @Column(name = "band_thresholds", columnDefinition = "numeric[]")
    private List<BigDecimal> bandThresholds;

    @Column(name = "band_scores", columnDefinition = "numeric[]")
    private List<BigDecimal> bandScores;

    @Column(name = "weight_within_category")
    private BigDecimal weightWithinCategory;

    @Column(name = "higher_is_better")
    private boolean higherIsBetter;

    private boolean active;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * v2: optional sector override. NULL = the default rule applied to every
     * company; a non-null value replaces the default rule with the same
     * (category, metric_key) for companies in that sector only.
     */
    private String sector;
}
