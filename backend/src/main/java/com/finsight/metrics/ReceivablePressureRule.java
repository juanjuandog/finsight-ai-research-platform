package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class ReceivablePressureRule extends AbstractRiskRule {
    @Override
    public String code() {
        return "HIGH_RECEIVABLE_PRESSURE";
    }

    @Override
    public List<RiskSignal> evaluate(String companySymbol, Map<MetricKey, FinancialMetric> metrics) {
        List<RiskSignal> signals = new ArrayList<>();
        metrics.keySet().stream()
                .map(MetricKey::fiscalYear)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .forEach(year -> {
                    BigDecimal arRevenue = value(metrics, year, "AR_REVENUE");
                    BigDecimal growthSpread = value(metrics, year, "RECEIVABLE_GROWTH_SPREAD");
                    if (arRevenue.compareTo(new BigDecimal("0.25")) > 0) {
                        signals.add(signal(
                                companySymbol,
                                code(),
                                year + " 应收账款压力偏高",
                                "应收账款/营收超过 25%，需要关注收入确认质量和回款压力。",
                                2
                        ));
                    }
                    if (growthSpread.compareTo(new BigDecimal("0.15")) > 0) {
                        signals.add(signal(
                                companySymbol,
                                "RECEIVABLE_GROWS_FASTER_THAN_REVENUE",
                                year + " 应收账款增速明显快于营收",
                                "应收账款同比增速高于营收同比增速 15 个百分点以上，可能意味着回款周期拉长。",
                                3
                        ));
                    }
                });
        return signals;
    }
}

