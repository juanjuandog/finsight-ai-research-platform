package com.finsight.domain.model;

import java.math.BigDecimal;
import java.time.Year;

public record FinancialMetric(
        String companySymbol,
        Year fiscalYear,
        String code,
        String name,
        BigDecimal value,
        String formulaVersion
) {
}

