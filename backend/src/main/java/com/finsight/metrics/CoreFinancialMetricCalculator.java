package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.FinancialStatement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class CoreFinancialMetricCalculator implements FinancialMetricCalculator {
    private static final String VERSION = "core-metric-v1";

    @Override
    public List<FinancialMetric> calculate(List<FinancialStatement> statements) {
        List<FinancialMetric> metrics = new ArrayList<>();
        for (FinancialStatement statement : statements) {
            metrics.add(metric(statement, "GROSS_MARGIN", "毛利率", ratio(statement.grossProfit(), statement.revenue())));
            metrics.add(metric(statement, "NET_MARGIN", "净利率", ratio(statement.netProfit(), statement.revenue())));
            metrics.add(metric(statement, "ROE", "净资产收益率", ratio(statement.netProfit(), statement.equity())));
            metrics.add(metric(statement, "DEBT_RATIO", "资产负债率", ratio(statement.totalLiabilities(), statement.totalAssets())));
            metrics.add(metric(statement, "OCF_NET_PROFIT", "经营现金流/净利润", ratio(statement.operatingCashFlow(), statement.netProfit())));
            metrics.add(metric(statement, "AR_REVENUE", "应收账款/营收", ratio(statement.accountsReceivable(), statement.revenue())));
        }
        return metrics;
    }

    private FinancialMetric metric(FinancialStatement statement, String code, String name, BigDecimal value) {
        return new FinancialMetric(statement.companySymbol(), statement.fiscalYear(), code, name, value, VERSION);
    }
}

