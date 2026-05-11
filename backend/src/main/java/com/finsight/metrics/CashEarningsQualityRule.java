package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class CashEarningsQualityRule extends AbstractRiskRule {
    @Override
    public String code() {
        return "WEAK_CASH_EARNINGS";
    }

    @Override
    public List<RiskSignal> evaluate(String companySymbol, Map<MetricKey, FinancialMetric> metrics) {
        List<RiskSignal> signals = new ArrayList<>();
        metrics.keySet().stream()
                .map(MetricKey::fiscalYear)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .forEach(year -> {
                    BigDecimal ocfProfit = value(metrics, year, "OCF_NET_PROFIT");
                    BigDecimal profitYoy = value(metrics, year, "NET_PROFIT_YOY");
                    BigDecimal ocfYoy = value(metrics, year, "OCF_YOY");
                    if (ocfProfit.compareTo(new BigDecimal("0.8")) < 0) {
                        signals.add(signal(
                                companySymbol,
                                code(),
                                year + " 利润现金含量偏弱",
                                "经营现金流/净利润低于 0.8，说明利润没有充分转化为现金流。",
                                2
                        ));
                    }
                    if (profitYoy.compareTo(BigDecimal.ZERO) > 0 && ocfYoy.compareTo(BigDecimal.ZERO) < 0) {
                        signals.add(signal(
                                companySymbol,
                                "PROFIT_CASH_FLOW_DIVERGENCE",
                                year + " 净利润增长但经营现金流下滑",
                                "净利润同比增长而经营现金流同比下降，需要关注利润质量和回款节奏。",
                                3
                        ));
                    }
                });
        return signals;
    }
}

