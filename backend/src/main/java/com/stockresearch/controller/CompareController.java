package com.stockresearch.controller;

import com.stockresearch.dto.CompareResponseDto;
import com.stockresearch.dto.MultiCompareResponseDto;
import com.stockresearch.service.CompareService;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/compare")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    // GET /api/compare?a=1&b=2 -- compare two companies already known by id
    @GetMapping
    public CompareResponseDto compare(@RequestParam Long a, @RequestParam Long b) {
        return compareService.compare(a, b);
    }

    // GET /api/compare/live?a=Reliance&b=TCS -- two free-text names, resolved and
    // scraped live (imported if new), then compared.
    @GetMapping("/live")
    public CompareResponseDto compareLive(@RequestParam String a, @RequestParam String b) {
        return compareService.compareByName(a, b);
    }

    // GET /api/compare/multi?ids=1,2,3 -- v2: compare 2 to 6 companies side by side.
    @GetMapping("/multi")
    public MultiCompareResponseDto compareMany(@RequestParam String ids) {
        List<Long> parsed;
        try {
            parsed = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::valueOf).toList();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ids must be a comma-separated list of company ids, e.g. ids=1,2,3");
        }
        return compareService.compareMany(parsed);
    }
}
