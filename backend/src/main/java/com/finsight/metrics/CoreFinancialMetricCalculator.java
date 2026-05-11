package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.FinancialStatement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CoreFinancialMetricCalculator implements FinancialMetricCalculator {
    private final MetricDefinitionCatalog definitionCatalog;

    public CoreFinancialMetricCalculator(MetricDefinitionCatalog definitionCatalog) {
        this.definitionCatalog = definitionCatalog;
    }

    @Override
    public List<FinancialMetric> calculate(List<FinancialStatement> statements) {
        MetricCalculationPlan plan = definitionCatalog.corePlan();
        Map<MetricKey, BigDecimal> context = new LinkedHashMap<>();
        Map<MetricKey, FinancialMetric> metrics = new LinkedHashMap<>();
        List<FinancialStatement> orderedStatements = statements.stream()
                .sorted(Comparator.comparing(FinancialStatement::fiscalYear))
                .toList();

        for (FinancialStatement statement : orderedStatements) {
            Year fiscalYear = statement.fiscalYear();
            for (MetricDefinition definition : plan.definitions()) {
                BigDecimal value = definition.formula().evaluate(statement, fiscalYear, context);
                MetricKey key = new MetricKey(fiscalYear, definition.code());
                context.put(key, value);
                metrics.put(key, new FinancialMetric(
                        statement.companySymbol(),
                        fiscalYear,
                        definition.code(),
                        definition.name(),
                        value,
                        plan.version()
                ));
            }
        }
        return List.copyOf(metrics.values());
    }
}
