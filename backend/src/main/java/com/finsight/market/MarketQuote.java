package com.finsight.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MarketQuote(
        String symbol,
        String exchange,
        String name,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal change,
        BigDecimal changePercent,
        LocalDate tradeDate,
        LocalTime tradeTime,
        String source,
        boolean realtime,
        String message
) {
    public LocalDateTime tradedAt() {
        if (tradeDate == null || tradeTime == null) {
            return null;
        }
        return LocalDateTime.of(tradeDate, tradeTime);
    }
}
