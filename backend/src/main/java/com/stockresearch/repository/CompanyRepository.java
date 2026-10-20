package com.stockresearch.repository;

import com.stockresearch.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByNseSymbolIgnoreCase(String nseSymbol);
    List<Company> findByNameContainingIgnoreCaseOrNseSymbolContainingIgnoreCase(String name, String symbol);
    List<Company> findBySectorIgnoreCase(String sector);
}
