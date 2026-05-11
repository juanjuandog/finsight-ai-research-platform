package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractRiskRule implements RiskRule {
    protected BigDecimal value(Map<MetricKey, FinancialMetric> metrics, Year year, String code) {
        FinancialMetric metric = metrics.get(new MetricKey(year, code));
        return metric == null ? BigDecimal.ZERO : metric.value();
    }

    protected RiskSignal signal(
            String companySymbol,
            String code,
            String title,
            String explanation,
            int severity
    ) {
        return new RiskSignal(
                UUID.nameUUIDFromBytes((companySymbol + ":" + code + ":" + title).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                companySymbol,
                code,
                title,
                explanation,
                severity,
                LocalDate.now()
        );
    }
}

