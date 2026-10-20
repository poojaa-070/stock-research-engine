package com.stockresearch.controller;

import com.stockresearch.dto.WatchlistItemDto;
import com.stockresearch.service.WatchlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** v2: single-user watchlist. */
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistItemDto> list() {
        return watchlistService.list();
    }

    @GetMapping("/{companyId}")
    public Map<String, Boolean> contains(@PathVariable Long companyId) {
        return Map.of("watching", watchlistService.contains(companyId));
    }

    // POST /api/watchlist/{companyId}  body (optional): {"note": "..."}
    @PostMapping("/{companyId}")
    public WatchlistItemDto add(@PathVariable Long companyId,
                                @RequestBody(required = false) Map<String, String> body) {
        return watchlistService.add(companyId, body == null ? null : body.get("note"));
    }

    @DeleteMapping("/{companyId}")
    public ResponseEntity<Void> remove(@PathVariable Long companyId) {
        watchlistService.remove(companyId);
        return ResponseEntity.noContent().build();
    }
}
