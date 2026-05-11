package com.finsight.domain.model;

import java.math.BigDecimal;
import java.time.Year;

public record FinancialStatement(
        String companySymbol,
        Year fiscalYear,
        BigDecimal revenue,
        BigDecimal grossProfit,
        BigDecimal netProfit,
        BigDecimal operatingCashFlow,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal equity,
        BigDecimal accountsReceivable
) {
}

