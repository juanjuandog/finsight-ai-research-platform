package com.finsight.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.domain.model.Company;
import com.finsight.domain.repository.CompanyRepository;
import com.finsight.market.ExchangeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class StockUniverseService {
    private static final Logger log = LoggerFactory.getLogger(StockUniverseService.class);
    private static final String EASTMONEY_STOCK_LIST = "https://push2.eastmoney.com/api/qt/clist/get";
    private static final String EASTMONEY_SEARCH = "https://search-codetable.eastmoney.com/codetable/search/web";
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 40;

    private final CompanyRepository companyRepository;
    private final ExchangeResolver exchangeResolver;
    private final ObjectMapper objectMapper;
    private final String aiServiceUrl;
    private final boolean freeProviderEnabled;

    public StockUniverseService(
            CompanyRepository companyRepository,
            ExchangeResolver exchangeResolver,
            ObjectMapper objectMapper,
            @Value("${finsight.ai-service-url:http://localhost:8001}") String aiServiceUrl,
            @Value("${finsight.stock-universe.free-provider-enabled:true}") boolean freeProviderEnabled
    ) {
        this.companyRepository = companyRepository;
        this.exchangeResolver = exchangeResolver;
        this.objectMapper = objectMapper;
        this.aiServiceUrl = aiServiceUrl;
        this.freeProviderEnabled = freeProviderEnabled;
    }

    public StockUniverseSyncResult syncAStocks() {
        List<String> providerErrors = new ArrayList<>();
        if (freeProviderEnabled) {
            try {
                return syncFromFreeProvider();
            } catch (RuntimeException ex) {
                String error = "free akshare provider unavailable: " + conciseError(ex);
                providerErrors.add(error);
                log.warn("Free stock universe provider failed, falling back to Eastmoney push2", ex);
            }
        }

        int saved = 0;
        int fetched = 0;
        int skipped = 0;
        List<String> samples = new ArrayList<>();
        int total = Integer.MAX_VALUE;
        boolean completed = false;
        Integer failedPage = null;
        String errorMessage = null;
        for (int page = 1; page <= MAX_PAGES && fetched < total; page++) {
            JsonNode data;
            try {
                data = requestPage(page);
            } catch (RuntimeException ex) {
                failedPage = page;
                errorMessage = conciseError(ex);
                log.warn("Stock universe sync stopped at page {} after fetching {} rows", page, fetched, ex);
                break;
            }
            total = data.path("total").asInt(total);
            JsonNode diff = data.path("diff");
            if (!diff.isArray() || diff.isEmpty()) {
                completed = true;
                break;
            }
            for (JsonNode item : diff) {
                fetched++;
                String symbol = text(item, "f12");
                String name = text(item, "f14");
                if (symbol == null || name == null || !exchangeResolver.isSupportedAStockCode(symbol)) {
                    skipped++;
                    continue;
                }
                Company company = new Company(
                        exchangeResolver.normalizeSymbol(symbol),
                        name,
                        exchangeResolver.exchangeOf(symbol),
                        normalizeIndustry(text(item, "f100"))
                );
                companyRepository.save(company);
                saved++;
                if (samples.size() < 8) {
                    samples.add(company.name() + " " + company.symbol());
                }
            }
            completed = fetched >= total;
        }
        if (!completed && errorMessage == null && fetched >= total) {
            completed = true;
        }
        if (!completed && errorMessage == null && fetched > 0) {
            errorMessage = "stock universe sync reached page limit " + MAX_PAGES + " before provider total " + total;
        }
        if (!providerErrors.isEmpty()) {
            errorMessage = String.join("; ", providerErrors)
                    + (errorMessage == null ? "" : "; " + errorMessage);
        }
        seedDemoCompanies();
        return new StockUniverseSyncResult(
                "eastmoney-push2",
                fetched,
                saved,
                skipped,
                companyRepository.count(),
                samples,
                Instant.now(),
                completed,
                failedPage,
                errorMessage
        );
    }

    private StockUniverseSyncResult syncFromFreeProvider() {
        URI uri = URI.create(trimTrailingSlash(aiServiceUrl) + "/stocks/a-shares?refresh=true");
        try {
            JsonNode json = objectMapper.readTree(readBody(uri));
            if (!json.path("completed").asBoolean(false)) {
                throw new IllegalStateException("provider returned incomplete result: " + json.path("error").asText("unknown error"));
            }
            JsonNode stocks = json.path("stocks");
            if (!stocks.isArray() || stocks.isEmpty()) {
                throw new IllegalStateException("provider returned empty stock list");
            }
            int saved = 0;
            int skipped = 0;
            List<String> samples = new ArrayList<>();
            for (JsonNode item : stocks) {
                String symbol = text(item, "symbol");
                String name = text(item, "name");
                if (symbol == null || name == null || !exchangeResolver.isSupportedAStockCode(symbol)) {
                    skipped++;
                    continue;
                }
                Company company = new Company(
                        exchangeResolver.normalizeSymbol(symbol),
                        name,
                        exchangeResolver.exchangeOf(symbol),
                        normalizeIndustry(text(item, "industry"))
                );
                companyRepository.save(company);
                saved++;
                if (samples.size() < 8) {
                    samples.add(company.name() + " " + company.symbol());
                }
            }
            return new StockUniverseSyncResult(
                    json.path("source").asText("akshare-free-provider"),
                    stocks.size(),
                    saved,
                    skipped,
                    companyRepository.count(),
                    samples,
                    Instant.now(),
                    true,
                    null,
                    null
            );
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read free stock universe provider", ex);
        }
    }

    public List<Company> search(String query, int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 50);
        List<Company> local = companyRepository.search(query, boundedLimit);
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank() || local.size() >= boundedLimit) {
            return local;
        }
        Map<String, Company> merged = new LinkedHashMap<>();
        local.forEach(company -> merged.put(company.symbol(), company));
        for (Company company : searchRemote(normalized, boundedLimit)) {
            merged.putIfAbsent(company.symbol(), company);
            companyRepository.save(company);
            if (merged.size() >= boundedLimit) {
                break;
            }
        }
        return merged.values().stream().limit(boundedLimit).toList();
    }

    public Company resolveAStock(String query) {
        String normalized = exchangeResolver.normalizeSymbol(query);
        Company company = companyRepository.findBySymbol(normalized)
                .or(() -> searchRemote(normalized, 1).stream().findFirst())
                .orElseGet(() -> new Company(normalized, "股票 " + normalized, exchangeResolver.exchangeOf(normalized), "待分类"));
        companyRepository.save(company);
        return company;
    }

    private JsonNode requestPage(int page) {
        URI uri = URI.create(EASTMONEY_STOCK_LIST
                + "?pn=" + page
                + "&pz=" + PAGE_SIZE
                + "&po=1"
                + "&np=1"
                + "&ut=bd1d9ddb04089700cf9c27f6f7426281"
                + "&fltt=2"
                + "&invt=2"
                + "&fid=f3"
                + "&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,m:0+t:81,m:0+t:7,m:0+t:64,m:0+t:50"
                + "&fields=f12,f14,f13,f100");
        try {
            JsonNode json = objectMapper.readTree(readBody(uri));
            if (json.path("rc").asInt(-1) != 0) {
                throw new IllegalStateException("stock universe provider rejected request: rc=" + json.path("rc").asText());
            }
            return json.path("data");
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read stock universe", ex);
        }
    }

    private String readBody(URI uri) throws IOException {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
            connection.setConnectTimeout((int) Duration.ofSeconds(3).toMillis());
            connection.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            connection.setRequestProperty("Referer", "https://quote.eastmoney.com/");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 FinSight");
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                throw new IOException("stock universe provider returned " + status);
            }
            try (InputStream input = stream) {
                String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                if (status >= 400) {
                    throw new IOException("stock universe provider returned " + status + ": " + body);
                }
                return body;
            }
        } catch (IOException ex) {
            return readBodyWithCurl(uri, ex);
        }
    }

    private String readBodyWithCurl(URI uri, IOException cause) throws IOException {
        Process process = new ProcessBuilder(
                "curl",
                "-fsSL",
                "--max-time",
                "8",
                "-A",
                "Mozilla/5.0 FinSight",
                "-H",
                "Referer: https://quote.eastmoney.com/",
                uri.toString()
        )
                .redirectErrorStream(true)
                .start();
        try {
            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("stock universe curl fallback timed out", cause);
            }
            if (process.exitValue() != 0) {
                throw new IOException("stock universe curl fallback failed: " + output, cause);
            }
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("stock universe curl fallback interrupted", ex);
        }
    }

    private List<Company> searchRemote(String query, int limit) {
        URI uri = URI.create(EASTMONEY_SEARCH
                + "?client=web"
                + "&clientType=webSuggest"
                + "&clientVersion=lastest"
                + "&keyword=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&pageIndex=1"
                + "&pageSize=" + Math.min(Math.max(limit, 1), 50));
        try {
            JsonNode json = objectMapper.readTree(readBody(uri));
            if (!"0".equals(json.path("code").asText())) {
                throw new IllegalStateException("search provider rejected request: " + json.path("msg").asText());
            }
            JsonNode result = json.path("result");
            if (!result.isArray()) {
                return List.of();
            }
            List<Company> companies = new ArrayList<>();
            for (JsonNode item : result) {
                String symbol = text(item, "code");
                String name = text(item, "shortName");
                String securityTypeName = text(item, "securityTypeName");
                if (symbol == null
                        || name == null
                        || !exchangeResolver.isSupportedAStockCode(symbol)
                        || securityTypeName == null
                        || !securityTypeName.endsWith("A")) {
                    continue;
                }
                companies.add(new Company(
                        exchangeResolver.normalizeSymbol(symbol),
                        name,
                        exchangeResolver.exchangeOf(symbol),
                        securityTypeName
                ));
            }
            return companies;
        } catch (RuntimeException | IOException ex) {
            log.warn("Remote stock search failed for query {}", query, ex);
            return List.of();
        }
    }

    private void seedDemoCompanies() {
        List.of(
                new Company("600519", "贵州茅台", "SH", "白酒"),
                new Company("300750", "宁德时代", "SZ", "新能源电池"),
                new Company("000001", "平安银行", "SZ", "银行"),
                new Company("002594", "比亚迪", "SZ", "新能源汽车")
        ).forEach(company -> {
            if (companyRepository.findBySymbol(company.symbol()).isEmpty()) {
                companyRepository.save(company);
            }
        });
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private String normalizeIndustry(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return "待分类";
        }
        return value;
    }

    private String conciseError(RuntimeException ex) {
        Throwable cursor = ex;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            return cursor.getClass().getSimpleName();
        }
        return cursor.getClass().getSimpleName() + ": " + message;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8001";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record StockUniverseSyncResult(
            String source,
            int fetched,
            int saved,
            int skipped,
            long companyCount,
            List<String> samples,
            Instant syncedAt,
            boolean completed,
            Integer failedPage,
            String errorMessage
    ) {
    }
}
