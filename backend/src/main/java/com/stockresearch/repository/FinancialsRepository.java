package com.stockresearch.repository;

import com.stockresearch.model.Financials;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FinancialsRepository extends JpaRepository<Financials, Long> {
    List<Financials> findByCompanyIdAndPeriodTypeOrderByPeriodEndDateAsc(Long companyId, String periodType);
}
