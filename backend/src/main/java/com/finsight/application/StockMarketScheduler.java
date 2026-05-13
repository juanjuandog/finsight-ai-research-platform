package com.finsight.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "finsight.scheduler", name = "enabled", havingValue = "true")
public class StockMarketScheduler {
    private static final Logger log = LoggerFactory.getLogger(StockMarketScheduler.class);

    private final StockUniverseService stockUniverseService;
    private final StockAnalysisApplicationService stockAnalysisApplicationService;
    private final int batchLimit;
    private final AtomicBoolean stockUniverseRunning = new AtomicBoolean(false);
    private final AtomicBoolean batchAnalysisRunning = new AtomicBoolean(false);

    public StockMarketScheduler(
            StockUniverseService stockUniverseService,
            StockAnalysisApplicationService stockAnalysisApplicationService,
            @Value("${finsight.scheduler.batch-limit:20}") int batchLimit
    ) {
        this.stockUniverseService = stockUniverseService;
        this.stockAnalysisApplicationService = stockAnalysisApplicationService;
        this.batchLimit = batchLimit;
    }

    @Scheduled(cron = "${finsight.scheduler.stock-universe-cron:0 20 8 * * MON-FRI}", zone = "${finsight.scheduler.zone:Asia/Shanghai}")
    public void syncStockUniverse() {
        if (!stockUniverseRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            StockUniverseService.StockUniverseSyncResult result = stockUniverseService.syncAStocks();
            log.info("Scheduled stock universe sync completed: source={}, saved={}, total={}",
                    result.source(), result.saved(), result.companyCount());
        } catch (RuntimeException ex) {
            log.warn("Scheduled stock universe sync failed", ex);
        } finally {
            stockUniverseRunning.set(false);
        }
    }

    @Scheduled(cron = "${finsight.scheduler.batch-analysis-cron:0 45 8 * * MON-FRI}", zone = "${finsight.scheduler.zone:Asia/Shanghai}")
    public void submitMorningBatchAnalysis() {
        if (!batchAnalysisRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            StockAnalysisApplicationService.BatchAnalysisResult result = stockAnalysisApplicationService.submitBatch(
                    new StockAnalysisApplicationService.BatchAnalysisRequest(List.of(), batchLimit)
            );
            log.info("Scheduled stock analysis submitted: requested={}, submitted={}, failed={}",
                    result.requested(), result.submitted(), result.failed());
        } catch (RuntimeException ex) {
            log.warn("Scheduled stock analysis failed", ex);
        } finally {
            batchAnalysisRunning.set(false);
        }
    }
}
