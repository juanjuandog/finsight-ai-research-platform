package com.finsight.api;

import com.finsight.domain.model.Company;
import com.finsight.domain.model.FinancialDocument;
import com.finsight.application.StockAiAnalysisService;
import com.finsight.application.StockAnalysisApplicationService;
import com.finsight.application.StockUniverseService;
import com.finsight.domain.repository.CompanyRepository;
import com.finsight.domain.repository.DocumentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;
    private final StockUniverseService stockUniverseService;
    private final StockAnalysisApplicationService stockAnalysisApplicationService;
    private final StockAiAnalysisService stockAiAnalysisService;

    public CompanyController(
            CompanyRepository companyRepository,
            DocumentRepository documentRepository,
            StockUniverseService stockUniverseService,
            StockAnalysisApplicationService stockAnalysisApplicationService,
            StockAiAnalysisService stockAiAnalysisService
    ) {
        this.companyRepository = companyRepository;
        this.documentRepository = documentRepository;
        this.stockUniverseService = stockUniverseService;
        this.stockAnalysisApplicationService = stockAnalysisApplicationService;
        this.stockAiAnalysisService = stockAiAnalysisService;
    }

    @GetMapping
    public List<Company> companies(@RequestParam(defaultValue = "200") int limit) {
        return companyRepository.findAll().stream()
                .limit(Math.min(Math.max(limit, 1), 1000))
                .toList();
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("count", companyRepository.count());
    }

    @GetMapping("/search")
    public List<Company> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return stockUniverseService.search(q, limit);
    }

    @PostMapping("/sync-a-shares")
    public StockUniverseService.StockUniverseSyncResult syncAStocks() {
        return stockUniverseService.syncAStocks();
    }

    @PostMapping("/batch-analysis")
    public StockAnalysisApplicationService.BatchAnalysisResult batchAnalysis(
            @RequestBody(required = false) StockAnalysisApplicationService.BatchAnalysisRequest request
    ) {
        return stockAnalysisApplicationService.submitBatch(request);
    }

    @GetMapping("/{companySymbol}/analysis-status")
    public StockAnalysisApplicationService.StockAnalysisStatus analysisStatus(@PathVariable String companySymbol) {
        return stockAnalysisApplicationService.status(companySymbol);
    }

    @GetMapping("/{companySymbol}/ai-analysis")
    public StockAiAnalysisService.StockAiAnalysisResponse aiAnalysis(@PathVariable String companySymbol) {
        return stockAiAnalysisService.analyze(companySymbol);
    }

    @GetMapping("/{companySymbol}/ai-analysis/latest")
    public ResponseEntity<StockAiAnalysisService.StockAiAnalysisResponse> latestAiAnalysis(@PathVariable String companySymbol) {
        return stockAiAnalysisService.latest(companySymbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{companySymbol}/ai-analysis/history")
    public List<StockAiAnalysisService.StockAiAnalysisResponse> aiAnalysisHistory(
            @PathVariable String companySymbol,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return stockAiAnalysisService.history(companySymbol, limit);
    }

    @GetMapping("/{companySymbol}/documents")
    public List<FinancialDocument> documents(@PathVariable String companySymbol) {
        return documentRepository.findByCompanySymbol(companySymbol);
    }
}
