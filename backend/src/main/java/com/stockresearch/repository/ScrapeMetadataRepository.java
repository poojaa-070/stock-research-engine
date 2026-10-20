package com.stockresearch.repository;

import com.stockresearch.model.ScrapeMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScrapeMetadataRepository extends JpaRepository<ScrapeMetadata, Long> {
    Optional<ScrapeMetadata> findTopByCompanyIdAndSourceOrderByLastScrapedAtDesc(Long companyId, String source);
}
