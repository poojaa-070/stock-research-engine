package com.stockresearch.repository;

import com.stockresearch.model.Prices;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PricesRepository extends JpaRepository<Prices, Long> {
    List<Prices> findTop220ByCompanyIdOrderByTradeDateDesc(Long companyId);

    /** v2: ~1 calendar year of NSE sessions (NSE trades ~248 days/year), so the
     * 52-week range and 1-year change genuinely cover a year. */
    List<Prices> findTop260ByCompanyIdOrderByTradeDateDesc(Long companyId);

    java.util.Optional<Prices> findTopByCompanyIdOrderByTradeDateDesc(Long companyId);
}
