package com.finsight.market;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class SinaMarketDataClient implements MarketDataClient {
    private final ExchangeResolver exchangeResolver;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public SinaMarketDataClient(ExchangeResolver exchangeResolver) {
        this.exchangeResolver = exchangeResolver;
    }

    @Override
    public MarketQuote quote(String symbol) {
        String normalized = exchangeResolver.normalizeSymbol(symbol);
        String exchange = exchangeResolver.exchangeOf(normalized);
        String marketSymbol = exchangeResolver.sinaPrefix(normalized) + normalized;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://hq.sinajs.cn/list=" + marketSymbol))
                .timeout(Duration.ofSeconds(3))
                .header("Referer", "https://finance.sina.com.cn")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("market quote provider returned " + response.statusCode());
            }
            return parse(normalized, exchange, response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("market quote provider unavailable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("market quote request interrupted", ex);
        }
    }

    private MarketQuote parse(String symbol, String exchange, byte[] body) {
        String text = new String(body, Charset.forName("GBK"));
        int start = text.indexOf('"');
        int end = text.lastIndexOf('"');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("empty quote payload");
        }
        String[] fields = text.substring(start + 1, end).split(",", -1);
        if (fields.length < 32 || fields[0].isBlank()) {
            throw new IllegalStateException("quote not found for " + symbol);
        }

        BigDecimal open = decimal(fields[1]);
        BigDecimal previousClose = decimal(fields[2]);
        BigDecimal current = decimal(fields[3]);
        BigDecimal high = decimal(fields[4]);
        BigDecimal low = decimal(fields[5]);
        BigDecimal change = current.subtract(previousClose);
        BigDecimal changePercent = BigDecimal.ZERO;
        if (previousClose.compareTo(BigDecimal.ZERO) > 0) {
            changePercent = change.multiply(BigDecimal.valueOf(100))
                    .divide(previousClose, 2, RoundingMode.HALF_UP);
        }

        return new MarketQuote(
                symbol,
                exchange,
                fields[0],
                current,
                previousClose,
                open,
                high,
                low,
                change,
                changePercent,
                parseDate(fields[30]),
                parseTime(fields[31]),
                "SINA_QUOTE",
                true,
                "实时行情接入成功"
        );
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalTime.parse(value);
    }
}
