package com.stockresearch.repository;

import com.stockresearch.model.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    List<Watchlist> findAllByOrderByAddedAtDesc();
    Optional<Watchlist> findByCompanyId(Long companyId);
    boolean existsByCompanyId(Long companyId);
}
