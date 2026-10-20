package com.stockresearch.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@Getter
@Setter
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "nse_symbol")
    private String nseSymbol;

    @Column(name = "bse_code")
    private String bseCode;

    private String sector;
    private String industry;

    @Column(name = "screener_url")
    private String screenerUrl;

    @Column(name = "business_description", columnDefinition = "TEXT")
    private String businessDescription;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
