package com.stockresearch.repository;

import com.stockresearch.model.NewsItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {
    List<NewsItem> findByCompanyIdOrderByPublishedAtDesc(Long companyId);
}
