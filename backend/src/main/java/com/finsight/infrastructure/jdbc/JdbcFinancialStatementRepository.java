package com.finsight.infrastructure.jdbc;

import com.finsight.domain.model.FinancialStatement;
import com.finsight.domain.repository.FinancialStatementRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;

@Repository
@Profile("postgres")
public class JdbcFinancialStatementRepository implements FinancialStatementRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcFinancialStatementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(FinancialStatement statement) {
        jdbcTemplate.update("""
                INSERT INTO financial_statements(
                    company_symbol, fiscal_year, revenue, gross_profit, net_profit,
                    operating_cash_flow, total_assets, total_liabilities, equity, accounts_receivable, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (company_symbol, fiscal_year)
                DO UPDATE SET revenue = EXCLUDED.revenue,
                              gross_profit = EXCLUDED.gross_profit,
                              net_profit = EXCLUDED.net_profit,
                              operating_cash_flow = EXCLUDED.operating_cash_flow,
                              total_assets = EXCLUDED.total_assets,
                              total_liabilities = EXCLUDED.total_liabilities,
                              equity = EXCLUDED.equity,
                              accounts_receivable = EXCLUDED.accounts_receivable,
                              updated_at = now()
                """,
                statement.companySymbol(),
                statement.fiscalYear().getValue(),
                statement.revenue(),
                statement.grossProfit(),
                statement.netProfit(),
                statement.operatingCashFlow(),
                statement.totalAssets(),
                statement.totalLiabilities(),
                statement.equity(),
                statement.accountsReceivable()
        );
    }

    @Override
    public List<FinancialStatement> findByCompanySymbol(String companySymbol) {
        return jdbcTemplate.query("""
                SELECT company_symbol, fiscal_year, revenue, gross_profit, net_profit,
                       operating_cash_flow, total_assets, total_liabilities, equity, accounts_receivable
                FROM financial_statements
                WHERE company_symbol = ?
                ORDER BY fiscal_year
                """, (rs, rowNum) -> new FinancialStatement(
                rs.getString("company_symbol"),
                Year.of(rs.getInt("fiscal_year")),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("gross_profit"),
                rs.getBigDecimal("net_profit"),
                rs.getBigDecimal("operating_cash_flow"),
                rs.getBigDecimal("total_assets"),
                rs.getBigDecimal("total_liabilities"),
                rs.getBigDecimal("equity"),
                rs.getBigDecimal("accounts_receivable")
        ), companySymbol);
    }
}

