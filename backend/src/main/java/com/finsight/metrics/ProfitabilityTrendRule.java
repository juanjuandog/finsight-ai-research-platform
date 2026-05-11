package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class ProfitabilityTrendRule extends AbstractRiskRule {
    @Override
    public String code() {
        return "PROFITABILITY_TREND_WEAKENING";
    }

    @Override
    public List<RiskSignal> evaluate(String companySymbol, Map<MetricKey, FinancialMetric> metrics) {
        List<Year> years = metrics.keySet().stream()
                .map(MetricKey::fiscalYear)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (years.size() < 3) {
            return List.of();
        }
        Year latest = years.get(years.size() - 1);
        BigDecimal latestMargin = value(metrics, latest, "GROSS_MARGIN");
        BigDecimal middleMargin = value(metrics, latest.minusYears(1), "GROSS_MARGIN");
        BigDecimal earliestMargin = value(metrics, latest.minusYears(2), "GROSS_MARGIN");
        if (latestMargin.compareTo(middleMargin) < 0 && middleMargin.compareTo(earliestMargin) < 0) {
            return List.of(signal(
                    companySymbol,
                    code(),
                    latest + " 毛利率连续下降",
                    "最近三期毛利率连续下降，可能反映价格体系、成本压力或产品结构变化。",
                    2
            ));
        }
        return List.of();
    }
}

