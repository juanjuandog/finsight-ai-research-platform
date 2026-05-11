package com.finsight.infrastructure.jdbc;

import com.finsight.domain.model.DocumentChunk;
import com.finsight.domain.model.DocumentType;
import com.finsight.domain.repository.DocumentChunkRepository;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@Profile("postgres")
public class JdbcDocumentChunkRepository implements DocumentChunkRepository {
    private final JdbcTemplate jdbcTemplate;
    private final JsonColumnMapper jsonColumnMapper;

    public JdbcDocumentChunkRepository(JdbcTemplate jdbcTemplate, JsonColumnMapper jsonColumnMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonColumnMapper = jsonColumnMapper;
    }

    @Override
    public void replaceChunks(String documentId, List<DocumentChunk> chunks) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
        for (DocumentChunk chunk : chunks) {
            jdbcTemplate.update("""
                    INSERT INTO document_chunks(
                        id, document_id, company_symbol, document_type, title, published_at, section,
                        chunk_index, text, content_hash, embedding, metadata, indexed_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                    ON CONFLICT (id)
                    DO UPDATE SET text = EXCLUDED.text,
                                  content_hash = EXCLUDED.content_hash,
                                  embedding = EXCLUDED.embedding,
                                  metadata = EXCLUDED.metadata,
                                  indexed_at = now()
                    """,
                    chunk.id(),
                    chunk.documentId(),
                    chunk.companySymbol(),
                    chunk.documentType().name(),
                    chunk.title(),
                    chunk.publishedAt(),
                    chunk.section(),
                    chunk.chunkIndex(),
                    chunk.text(),
                    chunk.contentHash(),
                    vector(chunk.embedding()),
                    jsonColumnMapper.jsonb(chunk.metadata())
            );
        }
    }

    @Override
    public List<DocumentChunk> findByDocumentId(String documentId) {
        return jdbcTemplate.query("""
                SELECT id, document_id, company_symbol, document_type, title, published_at, section,
                       chunk_index, text, content_hash, embedding::text, metadata::text
                FROM document_chunks
                WHERE document_id = ?
                ORDER BY chunk_index
                """, this::mapChunk, documentId);
    }

    @Override
    public List<DocumentChunk> keywordSearch(String companySymbol, String query, int limit) {
        String safeQuery = query == null || query.isBlank() ? "" : query;
        if (safeQuery.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, document_id, company_symbol, document_type, title, published_at, section,
                           chunk_index, text, content_hash, embedding::text, metadata::text
                    FROM document_chunks
                    WHERE (? IS NULL OR company_symbol = ?)
                    ORDER BY published_at DESC, chunk_index
                    LIMIT ?
                    """, this::mapChunk, companySymbol, companySymbol, limit);
        }
        return jdbcTemplate.query("""
                SELECT id, document_id, company_symbol, document_type, title, published_at, section,
                       chunk_index, text, content_hash, embedding::text, metadata::text
                FROM document_chunks
                WHERE (? IS NULL OR company_symbol = ?)
                  AND to_tsvector('simple', title || ' ' || section || ' ' || text) @@ plainto_tsquery('simple', ?)
                ORDER BY ts_rank(to_tsvector('simple', title || ' ' || section || ' ' || text), plainto_tsquery('simple', ?)) DESC,
                         published_at DESC
                LIMIT ?
                """, this::mapChunk, companySymbol, companySymbol, safeQuery, safeQuery, limit);
    }

    @Override
    public List<DocumentChunk> vectorSearch(String companySymbol, List<Double> embedding, int limit) {
        return jdbcTemplate.query("""
                SELECT id, document_id, company_symbol, document_type, title, published_at, section,
                       chunk_index, text, content_hash, embedding::text, metadata::text
                FROM document_chunks
                WHERE (? IS NULL OR company_symbol = ?)
                  AND embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """, this::mapChunk, companySymbol, companySymbol, vectorLiteral(embedding), limit);
    }

    @Override
    public long countByCompanySymbol(String companySymbol) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM document_chunks
                WHERE company_symbol = ?
                """, Long.class, companySymbol);
        return count == null ? 0 : count;
    }

    private DocumentChunk mapChunk(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentChunk(
                rs.getString("id"),
                rs.getString("document_id"),
                rs.getString("company_symbol"),
                DocumentType.valueOf(rs.getString("document_type")),
                rs.getString("title"),
                rs.getDate("published_at").toLocalDate(),
                rs.getString("section"),
                rs.getInt("chunk_index"),
                rs.getString("text"),
                rs.getString("content_hash"),
                parseVector(rs.getString("embedding")),
                jsonColumnMapper.stringMap(rs.getString("metadata"))
        );
    }

    private PGobject vector(List<Double> embedding) {
        try {
            PGobject object = new PGobject();
            object.setType("vector");
            object.setValue(vectorLiteral(embedding));
            return object;
        } catch (SQLException ex) {
            throw new IllegalArgumentException("Failed to serialize pgvector value", ex);
        }
    }

    private String vectorLiteral(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(value -> String.format(java.util.Locale.ROOT, "%.6f", value))
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private List<Double> parseVector(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.replace("[", "").replace("]", "");
        if (normalized.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(normalized.split(","))
                .map(String::trim)
                .map(Double::parseDouble)
                .toList();
    }
}

