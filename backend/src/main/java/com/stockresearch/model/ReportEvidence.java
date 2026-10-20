package com.stockresearch.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "report_evidence")
@Getter
@Setter
public class ReportEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id")
    private Long reportId;

    private String category;
    private String sentiment; // "positive" | "neutral" | "negative"

    @Column(nullable = false)
    private String statement;

    @Column(name = "source_ref")
    private String sourceRef;
}
