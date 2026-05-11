package com.finsight.market;

import com.finsight.domain.model.Company;
import com.finsight.domain.repository.CompanyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Service
public class MarketDataService {
    private final MarketDataClient marketDataClient;
    private final CompanyRepository companyRepository;

    public MarketDataService(MarketDataClient marketDataClient, CompanyRepository companyRepository) {
        this.marketDataClient = marketDataClient;
        this.companyRepository = companyRepository;
    }

    public MarketQuote quote(String symbol) {
        String normalized = normalizeSymbol(symbol);
        try {
            MarketQuote quote = marketDataClient.quote(normalized);
            companyRepository.save(new Company(
                    quote.symbol(),
                    quote.name(),
                    quote.exchange(),
                    companyRepository.findBySymbol(normalized).map(Company::industry).orElse("待分类")
            ));
            return quote;
        } catch (RuntimeException ex) {
            return fallbackQuote(normalized, ex.getMessage());
        }
    }

    private MarketQuote fallbackQuote(String symbol, String reason) {
        Company company = companyRepository.findBySymbol(symbol)
                .orElse(new Company(symbol, "股票 " + symbol, exchangeOf(symbol), "待分类"));
        return new MarketQuote(
                symbol,
                company.exchange(),
                company.name(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDate.now(),
                LocalTime.now().withNano(0),
                "FALLBACK",
                false,
                "实时行情暂不可用，已降级到本地分析数据：" + reason
        );
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        return symbol.trim().toUpperCase();
    }

    private String exchangeOf(String symbol) {
        if (symbol.startsWith("6") || symbol.startsWith("9")) {
            return "SH";
        }
        return "SZ";
    }
}
