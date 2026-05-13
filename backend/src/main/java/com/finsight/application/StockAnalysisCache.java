package com.finsight.application;

import java.time.Duration;
import java.util.Optional;

public interface StockAnalysisCache {
    Optional<StockAiAnalysisService.StockAiAnalysisResponse> get(String key);

    void put(String key, StockAiAnalysisService.StockAiAnalysisResponse response, Duration ttl);
}
