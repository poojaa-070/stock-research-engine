package com.stockresearch.repository;

import com.stockresearch.model.ScoringRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScoringRuleRepository extends JpaRepository<ScoringRule, Long> {
    List<ScoringRule> findByCategoryAndActiveTrue(String category);
    List<ScoringRule> findByActiveTrue();
}
