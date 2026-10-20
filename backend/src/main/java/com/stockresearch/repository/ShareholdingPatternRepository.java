package com.stockresearch.repository;

import com.stockresearch.model.ShareholdingPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ShareholdingPatternRepository extends JpaRepository<ShareholdingPattern, Long> {
    List<ShareholdingPattern> findByCompanyIdOrderByPeriodEndDateAsc(Long companyId);
}
