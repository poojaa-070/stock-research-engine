package com.stockresearch.controller;

import com.stockresearch.dto.CompanySummaryDto;
import com.stockresearch.exception.NotFoundException;
import com.stockresearch.model.Company;
import com.stockresearch.repository.CompanyRepository;
import com.stockresearch.service.ScraperClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;
    private final ScraperClient scraperClient;

    public CompanyController(CompanyRepository companyRepository, ScraperClient scraperClient) {
        this.companyRepository = companyRepository;
        this.scraperClient = scraperClient;
    }

    // GET /api/companies/search?q=hdfc
    // Local-only lookup -- fast, but only finds companies already imported.
    // If this comes back empty, the frontend offers /import instead.
    // v2: blank queries return everything (capped), sorted by name.
    @GetMapping("/search")
    public List<CompanySummaryDto> search(@RequestParam(defaultValue = "") String q) {
        String query = q.trim();
        return companyRepository
                .findByNameContainingIgnoreCaseOrNseSymbolContainingIgnoreCase(query, query)
                .stream()
                .sorted(java.util.Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(50)
                .map(this::toDto)
                .toList();
    }

    // POST /api/companies/import?query=reliance
    // Real-time lookup against Screener itself via the Python scraper
    // service, registering + scraping the company if it's new to us.
    @PostMapping("/import")
    public CompanySummaryDto importCompany(@RequestParam String query) {
        if (query.isBlank()) throw new IllegalArgumentException("Enter a company name or NSE symbol to import.");
        var result = scraperClient.importCompany(query.trim());
        Company company = companyRepository.findById(result.companyId())
                .orElseThrow(() -> new NotFoundException("Imported company " + result.companyId() + " not found"));
        return toDto(company);
    }

    @GetMapping("/{id}")
    public CompanySummaryDto get(@PathVariable Long id) {
        Company c = companyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No company with id " + id));
        return toDto(c);
    }

    private CompanySummaryDto toDto(Company c) {
        return new CompanySummaryDto(c.getId(), c.getName(), c.getNseSymbol(), c.getBseCode(), c.getSector(), c.getIndustry());
    }
}
