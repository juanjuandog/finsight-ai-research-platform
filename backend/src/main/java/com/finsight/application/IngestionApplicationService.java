package com.finsight.application;

import com.finsight.domain.FinancialDataIngestionTemplate;
import org.springframework.stereotype.Service;

@Service
public class IngestionApplicationService {
    private final FinancialDataIngestionTemplate dataSource;

    public IngestionApplicationService(FinancialDataIngestionTemplate dataSource) {
        this.dataSource = dataSource;
    }

    public FinancialDataIngestionTemplate.IngestionResult ingestDemoCompany() {
        return dataSource.ingestCompany("600519");
    }
}

