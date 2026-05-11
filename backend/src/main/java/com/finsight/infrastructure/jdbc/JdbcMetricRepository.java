package com.finsight.infrastructure.jdbc;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;
import com.finsight.domain.repository.MetricRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;

@Repository
@Profile("postgres")
public class JdbcMetricRepository implements MetricRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcMetricRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveMetric(FinancialMetric metric) {
        jdbcTemplate.update("""
                INSERT INTO financial_metrics(company_symbol, fiscal_year, code, name, value, formula_version, calculated_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (company_symbol, fiscal_year, code, formula_version)
                DO UPDATE SET name = EXCLUDED.name,
                              value = EXCLUDED.value,
                              calculated_at = now()
                """,
                metric.companySymbol(),
                metric.fiscalYear().getValue(),
                metric.code(),
                metric.name(),
                metric.value(),
                metric.formulaVersion()
        );
    }

    @Override
    public void saveRiskSignal(RiskSignal riskSignal) {
        jdbcTemplate.update("""
                INSERT INTO risk_signals(id, company_symbol, code, title, explanation, severity, detected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id)
                DO UPDATE SET title = EXCLUDED.title,
                              explanation = EXCLUDED.explanation,
                              severity = EXCLUDED.severity,
                              detected_at = EXCLUDED.detected_at
                """,
                riskSignal.id(),
                riskSignal.companySymbol(),
                riskSignal.code(),
                riskSignal.title(),
                riskSignal.explanation(),
                riskSignal.severity(),
                riskSignal.detectedAt()
        );
    }

    @Override
    public List<FinancialMetric> findMetrics(String companySymbol) {
        return jdbcTemplate.query("""
                SELECT company_symbol, fiscal_year, code, name, value, formula_version
                FROM financial_metrics
                WHERE company_symbol = ?
                ORDER BY fiscal_year, code
                """, (rs, rowNum) -> new FinancialMetric(
                rs.getString("company_symbol"),
                Year.of(rs.getInt("fiscal_year")),
                rs.getString("code"),
                rs.getString("name"),
                rs.getBigDecimal("value"),
                rs.getString("formula_version")
        ), companySymbol);
    }

    @Override
    public List<RiskSignal> findRiskSignals(String companySymbol) {
        return jdbcTemplate.query("""
                SELECT id, company_symbol, code, title, explanation, severity, detected_at
                FROM risk_signals
                WHERE company_symbol = ?
                ORDER BY detected_at DESC
                """, (rs, rowNum) -> new RiskSignal(
                rs.getString("id"),
                rs.getString("company_symbol"),
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("explanation"),
                rs.getInt("severity"),
                rs.getDate("detected_at").toLocalDate()
        ), companySymbol);
    }
}

