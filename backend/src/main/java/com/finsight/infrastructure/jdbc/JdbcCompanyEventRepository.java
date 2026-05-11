package com.finsight.infrastructure.jdbc;

import com.finsight.domain.model.CompanyEvent;
import com.finsight.domain.model.EventType;
import com.finsight.domain.repository.CompanyEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("postgres")
public class JdbcCompanyEventRepository implements CompanyEventRepository {
    private final JdbcTemplate jdbcTemplate;
    private final JsonColumnMapper jsonColumnMapper;

    public JdbcCompanyEventRepository(JdbcTemplate jdbcTemplate, JsonColumnMapper jsonColumnMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonColumnMapper = jsonColumnMapper;
    }

    @Override
    public void replaceCompanyEvents(String companySymbol, List<CompanyEvent> events) {
        jdbcTemplate.update("DELETE FROM company_events WHERE company_symbol = ?", companySymbol);
        for (CompanyEvent event : events) {
            jdbcTemplate.update("""
                    INSERT INTO company_events(id, company_symbol, event_type, happened_at, title, summary, evidence_document_ids)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    event.id(),
                    event.companySymbol(),
                    event.type().name(),
                    event.happenedAt(),
                    event.title(),
                    event.summary(),
                    jsonColumnMapper.jsonb(event.evidenceDocumentIds())
            );
        }
    }

    @Override
    public List<CompanyEvent> findByCompanySymbol(String companySymbol) {
        return jdbcTemplate.query("""
                SELECT id, company_symbol, event_type, happened_at, title, summary, evidence_document_ids::text
                FROM company_events
                WHERE company_symbol = ?
                ORDER BY happened_at DESC
                """, (rs, rowNum) -> new CompanyEvent(
                rs.getString("id"),
                rs.getString("company_symbol"),
                EventType.valueOf(rs.getString("event_type")),
                rs.getDate("happened_at").toLocalDate(),
                rs.getString("title"),
                rs.getString("summary"),
                jsonColumnMapper.stringList(rs.getString("evidence_document_ids"))
        ), companySymbol);
    }
}

