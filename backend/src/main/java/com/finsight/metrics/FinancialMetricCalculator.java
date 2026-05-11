package com.finsight.metrics;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.FinancialStatement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public interface FinancialMetricCalculator {
    List<FinancialMetric> calculate(List<FinancialStatement> statements);

    default BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || BigDecimal.ZERO.compareTo(denominator) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }
}

