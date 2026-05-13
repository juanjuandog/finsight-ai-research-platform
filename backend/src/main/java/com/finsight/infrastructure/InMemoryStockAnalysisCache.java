package com.finsight.infrastructure;

import com.finsight.application.StockAiAnalysisService;
import com.finsight.application.StockAnalysisCache;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("!redis")
public class InMemoryStockAnalysisCache implements StockAnalysisCache {
    private final ConcurrentHashMap<String, CacheEntry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<StockAiAnalysisService.StockAiAnalysisResponse> get(String key) {
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    @Override
    public void put(String key, StockAiAnalysisService.StockAiAnalysisResponse response, Duration ttl) {
        entries.put(key, new CacheEntry(response, Instant.now().plus(ttl)));
    }

    private record CacheEntry(
            StockAiAnalysisService.StockAiAnalysisResponse response,
            Instant expiresAt
    ) {
    }
}
