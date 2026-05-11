package com.finsight.domain.repository;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;

import java.util.List;

public interface MetricRepository {
    void saveMetric(FinancialMetric metric);

    void saveRiskSignal(RiskSignal riskSignal);

    List<FinancialMetric> findMetrics(String companySymbol);

    List<RiskSignal> findRiskSignals(String companySymbol);
}

