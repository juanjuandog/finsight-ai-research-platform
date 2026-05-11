package com.finsight.api;

import com.finsight.application.IngestionApplicationService;
import com.finsight.domain.FinancialDataIngestionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {
    private final IngestionApplicationService ingestionApplicationService;

    public IngestionController(IngestionApplicationService ingestionApplicationService) {
        this.ingestionApplicationService = ingestionApplicationService;
    }

    @PostMapping("/demo")
    public FinancialDataIngestionTemplate.IngestionResult ingestDemo() {
        return ingestionApplicationService.ingestDemoCompany();
    }
}

