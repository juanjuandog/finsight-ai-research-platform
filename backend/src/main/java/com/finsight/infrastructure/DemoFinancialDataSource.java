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
        return List.of(new Company("600519", "贵州茅台", "SSE", "白酒"));
    }

    @Override
    public List<FinancialDocument> fetchDocuments(String companySymbol) {
        return List.of(
                new FinancialDocument(
                        "doc-600519-2024-ar",
                        companySymbol,
                        DocumentType.ANNUAL_REPORT,
                        "贵州茅台 2024 年年度报告摘要",
                        LocalDate.of(2025, 3, 31),
                        "demo://600519/annual-report-2024",
                        "公司 2024 年营业收入保持增长，毛利率维持高位。管理层讨论中提到渠道结构优化和直营占比提升。经营现金流与净利润匹配度较高，整体现金回款质量稳定。",
                        Map.of("section", "管理层讨论与分析", "fiscalYear", "2024")
                ),
                new FinancialDocument(
                        "doc-600519-risk-note",
                        companySymbol,
                        DocumentType.RESEARCH_REPORT,
                        "白酒行业需求分化下的龙头公司观察",
                        LocalDate.of(2025, 5, 10),
                        "demo://600519/research-note",
                        "研报认为高端白酒需求韧性仍强，但行业库存和消费场景恢复节奏存在不确定性。建议关注渠道库存、批价稳定性和费用投放效率。",
                        Map.of("section", "风险提示", "institution", "Demo Securities")
                )
        );
    }

    @Override
    public List<FinancialStatement> fetchStatements(String companySymbol) {
        return List.of(
                new FinancialStatement(companySymbol, Year.of(2022), bd("127554000000"), bd("116800000000"), bd("62716000000"), bd("64000000000"), bd("254000000000"), bd("52000000000"), bd("202000000000"), bd("4500000000")),
                new FinancialStatement(companySymbol, Year.of(2023), bd("150560000000"), bd("138200000000"), bd("74734000000"), bd("74000000000"), bd("276000000000"), bd("57500000000"), bd("218500000000"), bd("5200000000")),
                new FinancialStatement(companySymbol, Year.of(2024), bd("174100000000"), bd("160300000000"), bd("86220000000"), bd("88000000000"), bd("301000000000"), bd("61800000000"), bd("239200000000"), bd("5900000000"))
        );
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}

