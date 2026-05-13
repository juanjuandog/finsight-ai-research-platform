package com.finsight.application;

import com.finsight.domain.model.Company;
import com.finsight.domain.model.FinancialDocument;
import com.finsight.domain.model.MetricCalculationRun;
import com.finsight.domain.repository.CompanyRepository;
import com.finsight.domain.repository.DocumentRepository;
import com.finsight.domain.repository.StockAnalysisReportRepository;
import com.finsight.market.ExchangeResolver;
import com.finsight.market.MarketDataService;
import com.finsight.market.MarketQuote;
import com.finsight.workflow.WorkflowTask;
import com.finsight.workflow.WorkflowTaskRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class StockAnalysisApplicationService {
    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;
    private final DocumentIndexingService documentIndexingService;
    private final MetricApplicationService metricApplicationService;
    private final IngestionApplicationService ingestionApplicationService;
    private final MarketDataService marketDataService;
    private final WorkflowTaskRepository workflowTaskRepository;
    private final ExchangeResolver exchangeResolver;
    private final StockUniverseService stockUniverseService;
    private final StockAnalysisReportRepository reportRepository;

    public StockAnalysisApplicationService(
            CompanyRepository companyRepository,
            DocumentRepository documentRepository,
            DocumentIndexingService documentIndexingService,
            MetricApplicationService metricApplicationService,
            IngestionApplicationService ingestionApplicationService,
            MarketDataService marketDataService,
            WorkflowTaskRepository workflowTaskRepository,
            ExchangeResolver exchangeResolver,
            StockUniverseService stockUniverseService,
            StockAnalysisReportRepository reportRepository
    ) {
        this.companyRepository = companyRepository;
        this.documentRepository = documentRepository;
        this.documentIndexingService = documentIndexingService;
        this.metricApplicationService = metricApplicationService;
        this.ingestionApplicationService = ingestionApplicationService;
        this.marketDataService = marketDataService;
        this.workflowTaskRepository = workflowTaskRepository;
        this.exchangeResolver = exchangeResolver;
        this.stockUniverseService = stockUniverseService;
        this.reportRepository = reportRepository;
    }

    public BatchAnalysisResult submitBatch(BatchAnalysisRequest request) {
        List<String> symbols = resolveSymbols(request);
        List<BatchAnalysisItem> items = symbols.stream()
                .map(this::submitOne)
                .toList();
        long submitted = items.stream().filter(item -> item.submitted()).count();
        return new BatchAnalysisResult(symbols.size(), (int) submitted, items.size() - (int) submitted, items);
    }

    public StockAnalysisStatus status(String symbol) {
        String normalized = exchangeResolver.normalizeSymbol(symbol);
        Company company = companyRepository.findBySymbol(normalized)
                .orElseGet(() -> stockUniverseService.resolveAStock(normalized));
        MarketQuote quote = marketDataService.quote(normalized);
        List<FinancialDocument> documents = documentRepository.findByCompanySymbol(normalized);
        List<MetricCalculationRun> runs = metricApplicationService.runs(normalized);
        List<WorkflowTask> tasks = workflowTaskRepository.findAll().stream()
                .filter(task -> normalized.equals(String.valueOf(task.payload().get("companySymbol"))))
                .sorted(Comparator.comparing(WorkflowTask::createdAt).reversed())
                .toList();
        WorkflowTask latestTask = tasks.isEmpty() ? null : tasks.get(0);
        LocalDate latestDocumentDate = documents.stream()
                .map(FinancialDocument::publishedAt)
                .max(LocalDate::compareTo)
                .orElse(null);
        Instant latestMetricRunAt = runs.stream()
                .map(MetricCalculationRun::finishedAt)
                .max(Instant::compareTo)
                .orElse(null);
        var latestReport = reportRepository.findLatest(normalized).orElse(null);
        return new StockAnalysisStatus(
                company,
                quote.realtime(),
                quote.tradedAt(),
                documents.size(),
                latestDocumentDate,
                documentIndexingService.countChunks(normalized),
                runs.size(),
                latestMetricRunAt,
                tasks.size(),
                latestTask == null ? null : latestTask.status().name(),
                latestTask == null ? null : latestTask.errorMessage(),
                latestTask == null ? null : latestTask.createdAt(),
                reportRepository.countByCompanySymbol(normalized),
                latestReport == null ? null : latestReport.generatedAt(),
                latestReport == null ? null : latestReport.model()
        );
    }

    private BatchAnalysisItem submitOne(String symbol) {
        String normalized = exchangeResolver.normalizeSymbol(symbol);
        try {
            if (!exchangeResolver.isSupportedAStockCode(normalized)) {
                throw new IllegalArgumentException("只支持 6 位 A 股股票代码");
            }
            Company company = stockUniverseService.resolveAStock(normalized);
            MarketQuote quote = marketDataService.quote(normalized);
            IngestionApplicationService.WorkflowSubmission submission = ingestionApplicationService.submitCompanyIngestion(normalized);
            return new BatchAnalysisItem(
                    normalized,
                    quote.name().startsWith("股票 ") ? company.name() : quote.name(),
                    true,
                    submission.taskId(),
                    submission.status(),
                    quote.realtime(),
                    null
            );
        } catch (RuntimeException ex) {
            return new BatchAnalysisItem(normalized, "股票 " + normalized, false, null, "FAILED", false, ex.getMessage());
        }
    }

    private List<String> resolveSymbols(BatchAnalysisRequest request) {
        if (request != null && request.symbols() != null && !request.symbols().isEmpty()) {
            int limit = request.limit() <= 0 ? request.symbols().size() : request.limit();
            return request.symbols().stream()
                    .map(exchangeResolver::normalizeSymbol)
                    .distinct()
                    .limit(Math.min(Math.max(limit, 1), 500))
                    .toList();
        }
        int limit = request == null || request.limit() <= 0 ? 20 : request.limit();
        return companyRepository.findAll().stream()
                .map(Company::symbol)
                .limit(Math.min(Math.max(limit, 1), 500))
                .toList();
    }
    public record BatchAnalysisRequest(List<String> symbols, int limit) {
    }

    public record BatchAnalysisResult(
            int requested,
            int submitted,
            int failed,
            List<BatchAnalysisItem> items
    ) {
    }

    public record BatchAnalysisItem(
            String symbol,
            String name,
            boolean submitted,
            String taskId,
            String status,
            boolean realtimeQuote,
            String error
    ) {
    }

    public record StockAnalysisStatus(
            Company company,
            boolean quoteRealtime,
            java.time.LocalDateTime quoteTradedAt,
            int documentCount,
            LocalDate latestDocumentDate,
            long chunkCount,
            int metricRunCount,
            Instant latestMetricRunAt,
            int workflowTaskCount,
            String latestWorkflowStatus,
            String latestWorkflowError,
            Instant latestWorkflowCreatedAt,
            long aiReportCount,
            Instant latestAiReportAt,
            String latestAiModel
    ) {
    }
}
