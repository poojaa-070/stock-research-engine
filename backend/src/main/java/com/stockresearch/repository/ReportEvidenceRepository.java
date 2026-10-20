package com.stockresearch.repository;

import com.stockresearch.model.ReportEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportEvidenceRepository extends JpaRepository<ReportEvidence, Long> {
    List<ReportEvidence> findByReportId(Long reportId);
    List<ReportEvidence> findByReportIdAndCategory(Long reportId, String category);
}
