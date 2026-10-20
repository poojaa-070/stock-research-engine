package com.stockresearch.repository;

import com.stockresearch.model.Ratios;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RatiosRepository extends JpaRepository<Ratios, Long> {
    List<Ratios> findByCompanyIdOrderByPeriodEndDateAsc(Long companyId);
    Optional<Ratios> findTopByCompanyIdOrderByPeriodEndDateDesc(Long companyId);
}
