package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class RiskSignalDetector {
    public List<RiskSignal> detect(String companySymbol, List<FinancialMetric> metrics) {
        List<RiskSignal> signals = new ArrayList<>();
        List<FinancialMetric> ocfRatios = metrics.stream()
                .filter(metric -> metric.code().equals("OCF_NET_PROFIT"))
                .sorted(Comparator.comparing(FinancialMetric::fiscalYear))
                .toList();
        ocfRatios.stream()
                .filter(metric -> metric.value().compareTo(new BigDecimal("0.8")) < 0)
                .forEach(metric -> signals.add(new RiskSignal(
                        UUID.randomUUID().toString(),
                        companySymbol,
                        "WEAK_CASH_EARNINGS",
                        metric.fiscalYear() + " 利润现金含量偏弱",
                        "经营现金流/净利润低于 0.8，说明利润增长可能没有充分转化为现金流。",
                        2,
                        LocalDate.now()
                )));

        List<FinancialMetric> arRatios = metrics.stream()
                .filter(metric -> metric.code().equals("AR_REVENUE"))
                .sorted(Comparator.comparing(FinancialMetric::fiscalYear))
                .toList();
        arRatios.stream()
                .filter(metric -> metric.value().compareTo(new BigDecimal("0.25")) > 0)
                .forEach(metric -> signals.add(new RiskSignal(
                        UUID.randomUUID().toString(),
                        companySymbol,
                        "HIGH_RECEIVABLE_PRESSURE",
                        metric.fiscalYear() + " 应收账款压力偏高",
                        "应收账款/营收超过 25%，需要关注收入确认质量和回款压力。",
                        2,
                        LocalDate.now()
                )));
        return signals;
    }
}

