package com.finsight.market;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketCandle(
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal close,
        BigDecimal high,
        BigDecimal low,
        long volume,
        BigDecimal amount,
        BigDecimal amplitude,
        BigDecimal changePercent,
        BigDecimal change,
        BigDecimal turnover
) {
}
