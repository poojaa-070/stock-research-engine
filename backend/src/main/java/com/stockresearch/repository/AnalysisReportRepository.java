package com.stockresearch.repository;

import com.stockresearch.model.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {
    List<AnalysisReport> findByCompanyIdOrderByGeneratedAtDesc(Long companyId);
    Optional<AnalysisReport> findTopByCompanyIdOrderByGeneratedAtDesc(Long companyId);
}
