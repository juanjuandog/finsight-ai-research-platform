package com.finsight.infrastructure.jdbc;

import com.finsight.domain.model.MetricCalculationRun;
import com.finsight.domain.repository.MetricCalculationRunRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
@Profile("postgres")
public class JdbcMetricCalculationRunRepository implements MetricCalculationRunRepository {
    private final JdbcTemplate jdbcTemplate;
    private final JsonColumnMapper jsonColumnMapper;

    public JdbcMetricCalculationRunRepository(JdbcTemplate jdbcTemplate, JsonColumnMapper jsonColumnMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonColumnMapper = jsonColumnMapper;
    }

    @Override
    public void save(MetricCalculationRun run) {
        jdbcTemplate.update("""
                INSERT INTO metric_calculation_runs(
                    id, company_symbol, plan_version, statement_count, metric_count, risk_signal_count,
                    started_at, finished_at, metadata
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                run.id(),
                run.companySymbol(),
                run.planVersion(),
                run.statementCount(),
                run.metricCount(),
                run.riskSignalCount(),
                Timestamp.from(run.startedAt()),
                Timestamp.from(run.finishedAt()),
                jsonColumnMapper.jsonb(run.metadata())
        );
    }

    @Override
    public List<MetricCalculationRun> findByCompanySymbol(String companySymbol) {
        return jdbcTemplate.query("""
                SELECT id, company_symbol, plan_version, statement_count, metric_count, risk_signal_count,
                       started_at, finished_at, metadata::text
                FROM metric_calculation_runs
                WHERE company_symbol = ?
                ORDER BY finished_at DESC
                """, (rs, rowNum) -> new MetricCalculationRun(
                rs.getString("id"),
                rs.getString("company_symbol"),
                rs.getString("plan_version"),
                rs.getInt("statement_count"),
                rs.getInt("metric_count"),
                rs.getInt("risk_signal_count"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at").toInstant(),
                jsonColumnMapper.objectMap(rs.getString("metadata"))
        ), companySymbol);
    }
}

