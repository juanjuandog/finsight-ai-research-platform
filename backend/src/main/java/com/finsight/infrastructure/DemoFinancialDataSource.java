package com.finsight.infrastructure;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DemoFinancialDataSource extends FinancialDataIngestionTemplate {
    public DemoFinancialDataSource(
            CompanyRepository companyRepository,
            DocumentRepository documentRepository,
            FinancialStatementRepository statementRepository,
            WorkflowTaskRepository taskRepository
    ) {
        super(companyRepository, documentRepository, statementRepository, taskRepository);
    }

    @Override
    public String sourceName() {
        return "demo-source";
    }

    @Override
    public List<Company> fetchCompanies() {
        return List.of(
                new Company("600519", "贵州茅台", "SSE", "白酒"),
                new Company("300750", "宁德时代", "SZSE", "新能源电池"),
                new Company("000001", "平安银行", "SZSE", "银行"),
                new Company("002594", "比亚迪", "SZSE", "新能源汽车")
        );
    }

    @Override
    public List<FinancialDocument> fetchDocuments(String companySymbol) {
        StockProfile profile = profile(companySymbol);
        return List.of(
                new FinancialDocument(
                        "doc-" + companySymbol + "-2024-ar",
                        companySymbol,
                        DocumentType.ANNUAL_REPORT,
                        profile.name() + " 2024 年年度报告摘要",
                        LocalDate.of(2025, 3, 31),
                        "demo://" + companySymbol + "/annual-report-2024",
                        profile.annualReportText(),
                        Map.of("section", "管理层讨论与分析", "fiscalYear", "2024")
                ),
                new FinancialDocument(
                        "doc-" + companySymbol + "-risk-note",
                        companySymbol,
                        DocumentType.RESEARCH_REPORT,
                        profile.industry() + "行业变化下的公司观察",
                        LocalDate.of(2025, 5, 10),
                        "demo://" + companySymbol + "/research-note",
                        profile.researchText(),
                        Map.of("section", "风险提示", "institution", "Demo Securities")
                )
        );
    }

    @Override
    public List<FinancialStatement> fetchStatements(String companySymbol) {
        StockProfile profile = profile(companySymbol);
        return List.of(
                statement(companySymbol, 2022, profile, "1.00"),
                statement(companySymbol, 2023, profile, "1.18"),
                statement(companySymbol, 2024, profile, "1.36")
        );
    }

    private FinancialStatement statement(String companySymbol, int year, StockProfile profile, String multiplier) {
        BigDecimal m = bd(multiplier);
        BigDecimal revenue = profile.baseRevenue().multiply(m);
        BigDecimal grossProfit = revenue.multiply(profile.grossMargin());
        BigDecimal netProfit = revenue.multiply(profile.netMargin());
        BigDecimal operatingCashFlow = netProfit.multiply(profile.cashConversion());
        BigDecimal totalAssets = revenue.multiply(profile.assetTurnoverBase());
        BigDecimal totalLiabilities = totalAssets.multiply(profile.debtRatio());
        BigDecimal equity = totalAssets.subtract(totalLiabilities);
        BigDecimal accountsReceivable = revenue.multiply(profile.receivableRatio());
        return new FinancialStatement(
                companySymbol,
                Year.of(year),
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

    private StockProfile profile(String companySymbol) {
        Optional<Company> company = fetchCompanies().stream()
                .filter(item -> item.symbol().equals(companySymbol))
                .findFirst();
        return switch (companySymbol) {
            case "600519" -> new StockProfile(
                    "贵州茅台", "白酒", bd("127554000000"), bd("0.92"), bd("0.49"), bd("1.02"), bd("1.72"), bd("0.21"), bd("0.034"),
                    "公司 2024 年营业收入保持增长，毛利率维持高位。管理层讨论中提到渠道结构优化和直营占比提升。经营现金流与净利润匹配度较高，整体现金回款质量稳定。",
                    "研报认为高端白酒需求韧性仍强，但行业库存和消费场景恢复节奏存在不确定性。建议关注渠道库存、批价稳定性和费用投放效率。"
            );
            case "300750" -> new StockProfile(
                    "宁德时代", "新能源电池", bd("328600000000"), bd("0.23"), bd("0.11"), bd("0.95"), bd("1.35"), bd("0.58"), bd("0.18"),
                    "公司动力电池和储能业务保持规模优势，海外客户拓展带动收入增长。管理层强调技术迭代、产能利用率和供应链成本控制是利润修复的核心变量。",
                    "研报认为新能源电池行业竞争加剧，价格压力仍需观察。建议重点跟踪海外订单、储能放量、原材料价格和应收账款周转。"
            );
            case "000001" -> new StockProfile(
                    "平安银行", "银行", bd("179900000000"), bd("0.48"), bd("0.26"), bd("1.08"), bd("18.00"), bd("0.91"), bd("0.03"),
                    "公司零售金融与对公业务协同推进，净息差承压背景下资产质量管理成为经营重点。拨备覆盖和不良生成率是分析盈利稳定性的关键。",
                    "研报认为银行板块受宏观信用周期和利率环境影响较大。建议关注净息差、资产质量、资本充足率和房地产链条风险暴露。"
            );
            case "002594" -> new StockProfile(
                    "比亚迪", "新能源汽车", bd("424100000000"), bd("0.20"), bd("0.06"), bd("0.88"), bd("1.08"), bd("0.64"), bd("0.12"),
                    "公司新能源汽车销量保持高位，垂直整合能力支撑成本优势。管理层关注出口增长、产品结构升级和电池业务外供进展。",
                    "研报认为新能源汽车行业价格竞争激烈，海外政策和汇率波动会影响盈利弹性。建议关注单车利润、库存水平和出口区域结构。"
            );
            default -> {
                String name = company.map(Company::name).orElse("股票 " + companySymbol);
                String industry = company.map(Company::industry).orElse("综合");
                yield new StockProfile(
                        name, industry, bd("88000000000"), bd("0.32"), bd("0.13"), bd("0.96"), bd("1.60"), bd("0.46"), bd("0.10"),
                        name + " 2024 年经营数据已接入系统。收入保持增长，盈利质量、现金流转化和资产负债结构是当前分析重点。",
                        industry + "行业景气度存在波动，建议结合收入增速、利润率、经营现金流和应收账款变化进行动态跟踪。"
                );
            }
        };
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record StockProfile(
            String name,
            String industry,
            BigDecimal baseRevenue,
            BigDecimal grossMargin,
            BigDecimal netMargin,
            BigDecimal cashConversion,
            BigDecimal assetTurnoverBase,
            BigDecimal debtRatio,
            BigDecimal receivableRatio,
            String annualReportText,
            String researchText
    ) {
    }
}
