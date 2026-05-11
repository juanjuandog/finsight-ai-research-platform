package com.finsight.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.domain.FinancialDataIngestionTemplate;
import com.finsight.domain.model.Company;
import com.finsight.domain.model.DocumentType;
import com.finsight.domain.model.FinancialDocument;
import com.finsight.domain.model.FinancialStatement;
import com.finsight.domain.repository.CompanyRepository;
import com.finsight.domain.repository.DocumentRepository;
import com.finsight.domain.repository.FinancialStatementRepository;
import com.finsight.workflow.WorkflowTaskRepository;
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
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class EastmoneyPublicDataSource extends FinancialDataIngestionTemplate {
    private static final String DATA_CENTER = "https://datacenter-web.eastmoney.com/api/data/v1/get";
    private static final String ANNOUNCEMENT_API = "https://np-anotice-stock.eastmoney.com/api/security/ann";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper objectMapper;

    public EastmoneyPublicDataSource(
            CompanyRepository companyRepository,
            DocumentRepository documentRepository,
            FinancialStatementRepository statementRepository,
            WorkflowTaskRepository taskRepository,
            ObjectMapper objectMapper
    ) {
        super(companyRepository, documentRepository, statementRepository, taskRepository);
        this.objectMapper = objectMapper;
    }

    @Override
    public String sourceName() {
        return "eastmoney-public";
    }

    @Override
    public List<Company> fetchCompanies() {
        return List.of(
                new Company("600519", "贵州茅台", "SH", "白酒"),
                new Company("300750", "宁德时代", "SZ", "新能源电池"),
                new Company("000001", "平安银行", "SZ", "银行"),
                new Company("002594", "比亚迪", "SZ", "新能源汽车")
        );
    }

    @Override
    public List<FinancialDocument> fetchDocuments(String companySymbol) {
        List<FinancialDocument> documents = new ArrayList<>();
        List<FinancialStatement> statements = fetchStatements(companySymbol);
        if (!statements.isEmpty()) {
            FinancialStatement latest = statements.get(statements.size() - 1);
            documents.add(new FinancialDocument(
                    "eastmoney-statement-summary-" + companySymbol + "-" + latest.fiscalYear(),
                    companySymbol,
                    DocumentType.ANNUAL_REPORT,
                    companySymbol + " " + latest.fiscalYear() + " 年公开财务数据摘要",
                    LocalDate.of(latest.fiscalYear().getValue(), 12, 31),
                    "https://data.eastmoney.com/bbsj/" + companySymbol + ".html",
                    statementSummary(latest),
                    Map.of("section", "东方财富公开财务报表", "fiscalYear", latest.fiscalYear().toString(), "source", "eastmoney")
            ));
        }
        documents.addAll(fetchAnnouncements(companySymbol));
        return documents;
    }

    @Override
    public List<FinancialStatement> fetchStatements(String companySymbol) {
        Map<Year, JsonNode> income = annualRows(companySymbol, "RPT_DMSK_FN_INCOME");
        Map<Year, JsonNode> balance = annualRows(companySymbol, "RPT_DMSK_FN_BALANCE");
        Map<Year, JsonNode> cashflow = annualRows(companySymbol, "RPT_DMSK_FN_CASHFLOW");

        return income.keySet().stream()
                .filter(year -> balance.containsKey(year) && cashflow.containsKey(year))
                .sorted()
                .skip(Math.max(0, income.keySet().stream().filter(year -> balance.containsKey(year) && cashflow.containsKey(year)).count() - 3))
                .map(year -> statement(companySymbol, year, income.get(year), balance.get(year), cashflow.get(year)))
                .toList();
    }

    private List<FinancialDocument> fetchAnnouncements(String companySymbol) {
        URI uri = UriComponentsBuilder.fromUriString(ANNOUNCEMENT_API)
                .queryParam("sr", "-1")
                .queryParam("page_size", "5")
                .queryParam("page_index", "1")
                .queryParam("ann_type", "A")
                .queryParam("client_source", "web")
                .queryParam("stock_list", companySymbol)
                .build(true)
                .toUri();
        JsonNode list = requestJson(uri).path("data").path("list");
        if (!list.isArray()) {
            return List.of();
        }
        List<FinancialDocument> documents = new ArrayList<>();
        for (JsonNode item : list) {
            String artCode = text(item, "art_code");
            String title = Optional.ofNullable(text(item, "title_ch")).orElse(text(item, "title"));
            LocalDate noticeDate = date(text(item, "notice_date"));
            String column = item.path("columns").isArray() && !item.path("columns").isEmpty()
                    ? text(item.path("columns").get(0), "column_name")
                    : "公告";
            documents.add(new FinancialDocument(
                    "eastmoney-ann-" + artCode,
                    companySymbol,
                    DocumentType.ANNOUNCEMENT,
                    title,
                    noticeDate,
                    "https://data.eastmoney.com/notices/detail/" + companySymbol + "/" + artCode + ".html",
                    "公告标题：" + title + "。公告分类：" + column + "。公告日期：" + noticeDate + "。该证据来自东方财富公开公告列表。",
                    Map.of("section", column, "source", "eastmoney-announcement", "artCode", artCode)
            ));
        }
        return documents;
    }

    private Map<Year, JsonNode> annualRows(String companySymbol, String reportName) {
        URI uri = UriComponentsBuilder.fromUriString(DATA_CENTER)
                .queryParam("sortColumns", "REPORT_DATE")
                .queryParam("sortTypes", "-1")
                .queryParam("pageSize", "20")
                .queryParam("pageNumber", "1")
                .queryParam("reportName", reportName)
                .queryParam("columns", "ALL")
                .queryParam("filter", "(SECURITY_CODE=\"" + companySymbol + "\")")
                .build()
                .encode()
                .toUri();
        JsonNode data = requestJson(uri).path("result").path("data");
        if (!data.isArray()) {
            return Map.of();
        }
        Map<Year, JsonNode> rows = new HashMap<>();
        for (JsonNode item : data) {
            LocalDate reportDate = date(text(item, "REPORT_DATE"));
            if (reportDate != null && reportDate.getMonthValue() == 12 && reportDate.getDayOfMonth() == 31) {
                rows.putIfAbsent(Year.of(reportDate.getYear()), item);
            }
        }
        return rows.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                .limit(5)
                .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), HashMap::putAll);
    }

    private FinancialStatement statement(String companySymbol, Year year, JsonNode income, JsonNode balance, JsonNode cashflow) {
        BigDecimal revenue = decimal(income, "TOTAL_OPERATE_INCOME");
        BigDecimal operatingCost = decimal(income, "OPERATE_COST");
        BigDecimal grossProfit = revenue.subtract(operatingCost);
        BigDecimal netProfit = decimal(income, "PARENT_NETPROFIT");
        BigDecimal operatingCashFlow = decimal(cashflow, "NETCASH_OPERATE");
        BigDecimal totalAssets = decimal(balance, "TOTAL_ASSETS");
        BigDecimal totalLiabilities = decimal(balance, "TOTAL_LIABILITIES");
        BigDecimal equity = decimal(balance, "TOTAL_EQUITY");
        BigDecimal accountsReceivable = decimal(balance, "ACCOUNTS_RECE");
        return new FinancialStatement(
                companySymbol,
                year,
                revenue,
                grossProfit,
                netProfit,
                operatingCashFlow,
                totalAssets,
                totalLiabilities,
                equity,
                accountsReceivable
        );
    }

    private String statementSummary(FinancialStatement statement) {
        return "公开财务报表显示，" + statement.companySymbol() + " " + statement.fiscalYear()
                + " 年营业收入为 " + statement.revenue()
                + "，归母净利润为 " + statement.netProfit()
                + "，经营现金流为 " + statement.operatingCashFlow()
                + "，总资产为 " + statement.totalAssets()
                + "，总负债为 " + statement.totalLiabilities()
                + "，应收账款为 " + statement.accountsReceivable()
                + "。以上字段来自东方财富公开财务报表接口。";
    }

    private JsonNode requestJson(URI uri) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "Mozilla/5.0 FinSight")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("public data provider returned " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            if (json.has("success") && !json.path("success").asBoolean()) {
                throw new IllegalStateException("public data provider rejected request: " + json.path("message").asText());
            }
            return json;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read public financial data", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("public financial data request interrupted", ex);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.asText());
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.substring(0, 10));
    }
}
