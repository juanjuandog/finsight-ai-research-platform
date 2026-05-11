package com.finsight.metrics;

import com.finsight.domain.model.FinancialStatement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class MetricDefinitionCatalog {
    public MetricCalculationPlan corePlan() {
        return new MetricCalculationPlan("metric-dag-v2", List.of(
                source("REVENUE", "营业收入", FinancialStatement::revenue),
                source("GROSS_PROFIT", "毛利润", FinancialStatement::grossProfit),
                source("NET_PROFIT", "净利润", FinancialStatement::netProfit),
                source("OPERATING_CASH_FLOW", "经营现金流", FinancialStatement::operatingCashFlow),
                source("TOTAL_ASSETS", "总资产", FinancialStatement::totalAssets),
                source("TOTAL_LIABILITIES", "总负债", FinancialStatement::totalLiabilities),
                source("EQUITY", "净资产", FinancialStatement::equity),
                source("ACCOUNTS_RECEIVABLE", "应收账款", FinancialStatement::accountsReceivable),
                derived("GROSS_MARGIN", "毛利率", List.of("GROSS_PROFIT", "REVENUE"),
                        (statement, year, context) -> ratio(value(context, year, "GROSS_PROFIT"), value(context, year, "REVENUE"))),
                derived("NET_MARGIN", "净利率", List.of("NET_PROFIT", "REVENUE"),
                        (statement, year, context) -> ratio(value(context, year, "NET_PROFIT"), value(context, year, "REVENUE"))),
                derived("ROE", "净资产收益率", List.of("NET_PROFIT", "EQUITY"),
                        (statement, year, context) -> ratio(value(context, year, "NET_PROFIT"), value(context, year, "EQUITY"))),
                derived("DEBT_RATIO", "资产负债率", List.of("TOTAL_LIABILITIES", "TOTAL_ASSETS"),
                        (statement, year, context) -> ratio(value(context, year, "TOTAL_LIABILITIES"), value(context, year, "TOTAL_ASSETS"))),
                derived("OCF_NET_PROFIT", "经营现金流/净利润", List.of("OPERATING_CASH_FLOW", "NET_PROFIT"),
                        (statement, year, context) -> ratio(value(context, year, "OPERATING_CASH_FLOW"), value(context, year, "NET_PROFIT"))),
                derived("AR_REVENUE", "应收账款/营收", List.of("ACCOUNTS_RECEIVABLE", "REVENUE"),
                        (statement, year, context) -> ratio(value(context, year, "ACCOUNTS_RECEIVABLE"), value(context, year, "REVENUE"))),
                derived("REVENUE_YOY", "营收同比增长率", List.of("REVENUE"),
                        (statement, year, context) -> yoy(context, year, "REVENUE")),
                derived("NET_PROFIT_YOY", "净利润同比增长率", List.of("NET_PROFIT"),
                        (statement, year, context) -> yoy(context, year, "NET_PROFIT")),
                derived("OCF_YOY", "经营现金流同比增长率", List.of("OPERATING_CASH_FLOW"),
                        (statement, year, context) -> yoy(context, year, "OPERATING_CASH_FLOW")),
                derived("AR_YOY", "应收账款同比增长率", List.of("ACCOUNTS_RECEIVABLE"),
                        (statement, year, context) -> yoy(context, year, "ACCOUNTS_RECEIVABLE")),
                derived("CASH_EARNINGS_GAP", "利润现金缺口", List.of("NET_PROFIT", "OPERATING_CASH_FLOW"),
                        (statement, year, context) -> value(context, year, "NET_PROFIT").subtract(value(context, year, "OPERATING_CASH_FLOW"))),
                derived("RECEIVABLE_GROWTH_SPREAD", "应收增速-营收增速", List.of("AR_YOY", "REVENUE_YOY"),
                        (statement, year, context) -> value(context, year, "AR_YOY").subtract(value(context, year, "REVENUE_YOY")))
        ));
    }

    private MetricDefinition source(String code, String name, SourceFormula formula) {
        return new MetricDefinition(code, name, List.of(), (statement, year, context) -> formula.evaluate(statement));
    }

    private MetricDefinition derived(String code, String name, List<String> dependencies, MetricDefinition.MetricFormula formula) {
        return new MetricDefinition(code, name, dependencies, formula);
    }

    private static BigDecimal value(Map<MetricKey, BigDecimal> context, java.time.Year year, String code) {
        return context.getOrDefault(new MetricKey(year, code), BigDecimal.ZERO);
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || BigDecimal.ZERO.compareTo(denominator) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 8, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal yoy(Map<MetricKey, BigDecimal> context, java.time.Year year, String code) {
        BigDecimal current = value(context, year, code);
        BigDecimal previous = value(context, year.minusYears(1), code);
        if (BigDecimal.ZERO.compareTo(previous) == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(previous).divide(previous, 8, java.math.RoundingMode.HALF_UP);
    }

    @FunctionalInterface
    private interface SourceFormula {
        BigDecimal evaluate(FinancialStatement statement);
    }
}

