package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RiskSignalDetector {
    private final List<RiskRule> rules;

    public RiskSignalDetector(List<RiskRule> rules) {
        this.rules = rules;
    }

    public List<RiskSignal> detect(String companySymbol, List<FinancialMetric> metrics) {
        Map<MetricKey, FinancialMetric> metricMap = metrics.stream()
                .collect(Collectors.toMap(
                        metric -> new MetricKey(metric.fiscalYear(), metric.code()),
                        metric -> metric,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
        return rules.stream()
                .flatMap(rule -> rule.evaluate(companySymbol, metricMap).stream())
                .toList();
    }
}
