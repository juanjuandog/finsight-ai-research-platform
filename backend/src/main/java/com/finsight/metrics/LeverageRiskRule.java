package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class LeverageRiskRule extends AbstractRiskRule {
    @Override
    public String code() {
        return "HIGH_LEVERAGE";
    }

    @Override
    public List<RiskSignal> evaluate(String companySymbol, Map<MetricKey, FinancialMetric> metrics) {
        return metrics.keySet().stream()
                .map(MetricKey::fiscalYear)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .filter(year -> value(metrics, year, "DEBT_RATIO").compareTo(new BigDecimal("0.65")) > 0)
                .map(year -> signal(
                        companySymbol,
                        code(),
                        year + " 资产负债率偏高",
                        "资产负债率高于 65%，需要结合行业属性评估偿债压力和财务弹性。",
                        2
                ))
                .toList();
    }
}

