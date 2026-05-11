package com.finsight.infrastructure.jdbc;

import com.finsight.domain.model.DocumentType;
import com.finsight.domain.model.FinancialDocument;
import com.finsight.domain.repository.DocumentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("postgres")
public class JdbcDocumentRepository implements DocumentRepository {
    private final JdbcTemplate jdbcTemplate;
    private final JsonColumnMapper jsonColumnMapper;

    public JdbcDocumentRepository(JdbcTemplate jdbcTemplate, JsonColumnMapper jsonColumnMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonColumnMapper = jsonColumnMapper;
    }

    @Override
    public void save(FinancialDocument document) {
        jdbcTemplate.update("""
                INSERT INTO financial_documents(
                    id, company_symbol, document_type, title, published_at, source_url, content, metadata, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (id)
                DO UPDATE SET company_symbol = EXCLUDED.company_symbol,
                              document_type = EXCLUDED.document_type,
                              title = EXCLUDED.title,
                              published_at = EXCLUDED.published_at,
                              source_url = EXCLUDED.source_url,
                              content = EXCLUDED.content,
                              metadata = EXCLUDED.metadata,
                              updated_at = now()
                """,
                document.id(),
                document.companySymbol(),
                document.type().name(),
                document.title(),
                document.publishedAt(),
                document.sourceUrl(),
                document.content(),
                jsonColumnMapper.jsonb(document.metadata())
        );
    }

    @Override
    public Optional<FinancialDocument> findById(String id) {
        return jdbcTemplate.query("""
                SELECT id, company_symbol, document_type, title, published_at, source_url, content, metadata::text
                FROM financial_documents
                WHERE id = ?
                """, this::mapDocument, id).stream().findFirst();
    }

    @Override
    public List<FinancialDocument> findByCompanySymbol(String companySymbol) {
        return jdbcTemplate.query("""
                SELECT id, company_symbol, document_type, title, published_at, source_url, content, metadata::text
                FROM financial_documents
                WHERE company_symbol = ?
                ORDER BY published_at DESC
                """, this::mapDocument, companySymbol);
    }

    @Override
    public List<FinancialDocument> search(String companySymbol, String query, int limit) {
        String safeQuery = query == null || query.isBlank() ? "" : query;
        if (safeQuery.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, company_symbol, document_type, title, published_at, source_url, content, metadata::text
                    FROM financial_documents
                    WHERE (? IS NULL OR company_symbol = ?)
                    ORDER BY published_at DESC
                    LIMIT ?
                    """, this::mapDocument, companySymbol, companySymbol, limit);
        }
        return jdbcTemplate.query("""
                SELECT id, company_symbol, document_type, title, published_at, source_url, content, metadata::text
                FROM financial_documents
                WHERE (? IS NULL OR company_symbol = ?)
                  AND to_tsvector('simple', title || ' ' || content) @@ plainto_tsquery('simple', ?)
                ORDER BY ts_rank(to_tsvector('simple', title || ' ' || content), plainto_tsquery('simple', ?)) DESC,
                         published_at DESC
                LIMIT ?
                """, this::mapDocument, companySymbol, companySymbol, safeQuery, safeQuery, limit);
    }

    private FinancialDocument mapDocument(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new FinancialDocument(
                rs.getString("id"),
                rs.getString("company_symbol"),
                DocumentType.valueOf(rs.getString("document_type")),
                rs.getString("title"),
                rs.getDate("published_at").toLocalDate(),
                rs.getString("source_url"),
                rs.getString("content"),
                jsonColumnMapper.stringMap(rs.getString("metadata"))
        );
    }
}

