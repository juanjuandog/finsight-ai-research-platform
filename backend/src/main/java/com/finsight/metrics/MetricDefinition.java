package com.finsight.metrics;

import com.finsight.domain.model.FinancialStatement;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Map;

public record MetricDefinition(
        String code,
        String name,
        List<String> dependencies,
        MetricFormula formula
) {
    @FunctionalInterface
    public interface MetricFormula {
        BigDecimal evaluate(FinancialStatement statement, Year fiscalYear, Map<MetricKey, BigDecimal> context);
    }
}

