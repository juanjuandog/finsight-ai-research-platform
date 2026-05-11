package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;

import java.util.List;
import java.util.Map;

public interface RiskRule {
    String code();

    List<RiskSignal> evaluate(String companySymbol, Map<MetricKey, FinancialMetric> metrics);
}

