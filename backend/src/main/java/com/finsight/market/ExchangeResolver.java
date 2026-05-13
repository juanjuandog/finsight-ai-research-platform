package com.finsight.market;

import org.springframework.stereotype.Component;

@Component
public class ExchangeResolver {
    public String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        return symbol.trim().toUpperCase();
    }

    public String exchangeOf(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized.startsWith("92") || normalized.startsWith("8") || normalized.startsWith("4")) {
            return "BJ";
        }
        if (normalized.startsWith("6") || normalized.startsWith("9")) {
            return "SH";
        }
        return "SZ";
    }

    public boolean isSupportedAStockCode(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (!normalized.matches("\\d{6}")) {
            return false;
        }
        return normalized.startsWith("60")
                || normalized.startsWith("68")
                || normalized.startsWith("69")
                || normalized.startsWith("00")
                || normalized.startsWith("30")
                || normalized.startsWith("92")
                || normalized.startsWith("8")
                || normalized.startsWith("4");
    }

    public String sinaPrefix(String symbol) {
        return exchangeOf(symbol).toLowerCase();
    }
}
