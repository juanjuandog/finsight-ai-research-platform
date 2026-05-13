package com.finsight.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class EastmoneyMarketHistoryClient {
    private static final String KLINE_API = "https://push2his.eastmoney.com/api/qt/stock/kline/get";

    private final ExchangeResolver exchangeResolver;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public EastmoneyMarketHistoryClient(ExchangeResolver exchangeResolver, ObjectMapper objectMapper) {
        this.exchangeResolver = exchangeResolver;
        this.objectMapper = objectMapper;
    }

    public List<MarketCandle> daily(String symbol, int limit) {
        String normalized = exchangeResolver.normalizeSymbol(symbol);
        int boundedLimit = Math.min(Math.max(limit, 20), 260);
        URI uri = UriComponentsBuilder.fromUriString(KLINE_API)
                .queryParam("secid", secid(normalized))
                .queryParam("fields1", "f1,f2,f3,f4,f5,f6")
                .queryParam("fields2", "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61")
                .queryParam("klt", "101")
                .queryParam("fqt", "0")
                .queryParam("beg", "0")
                .queryParam("end", "20500101")
                .queryParam("lmt", boundedLimit)
                .toUriString()
                .transform(URI::create);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 FinSight")
                .header("Referer", "https://quote.eastmoney.com/")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("market history provider returned " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            if (json.path("rc").asInt(-1) != 0) {
                throw new IllegalStateException("market history provider rejected request: rc=" + json.path("rc").asText());
            }
            JsonNode klines = json.path("data").path("klines");
            if (!klines.isArray() || klines.isEmpty()) {
                throw new IllegalStateException("market history provider returned empty kline list");
            }
            List<MarketCandle> candles = new ArrayList<>();
            for (JsonNode item : klines) {
                candles.add(parse(item.asText()));
            }
            return candles.stream()
                    .skip(Math.max(0, candles.size() - boundedLimit))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read market history", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("market history request interrupted", ex);
        }
    }

    private MarketCandle parse(String raw) {
        String[] fields = raw.split(",", -1);
        if (fields.length < 11) {
            throw new IllegalStateException("invalid kline payload: " + raw);
        }
        return new MarketCandle(
                LocalDate.parse(fields[0]),
                decimal(fields[1]),
                decimal(fields[2]),
                decimal(fields[3]),
                decimal(fields[4]),
                longValue(fields[5]),
                decimal(fields[6]),
                decimal(fields[7]),
                decimal(fields[8]),
                decimal(fields[9]),
                decimal(fields[10])
        );
    }

    private String secid(String symbol) {
        return ("SH".equals(exchangeResolver.exchangeOf(symbol)) ? "1." : "0.") + symbol;
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private long longValue(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return 0;
        }
        return new BigDecimal(value).longValue();
    }
}
