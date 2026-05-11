package com.finsight.infrastructure.jdbc;

import com.finsight.domain.model.EntityType;
import com.finsight.domain.model.KnowledgeEntity;
import com.finsight.domain.model.KnowledgeRelation;
import com.finsight.domain.repository.KnowledgeGraphRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("postgres")
public class JdbcKnowledgeGraphRepository implements KnowledgeGraphRepository {
    private final JdbcTemplate jdbcTemplate;
    private final JsonColumnMapper jsonColumnMapper;

    public JdbcKnowledgeGraphRepository(JdbcTemplate jdbcTemplate, JsonColumnMapper jsonColumnMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonColumnMapper = jsonColumnMapper;
    }

    @Override
    public void replaceCompanyGraph(String companySymbol, List<KnowledgeEntity> entities, List<KnowledgeRelation> relations) {
        jdbcTemplate.update("DELETE FROM knowledge_relations WHERE company_symbol = ?", companySymbol);
        jdbcTemplate.update("DELETE FROM knowledge_entities WHERE company_symbol = ?", companySymbol);
        for (KnowledgeEntity entity : entities) {
            jdbcTemplate.update("""
                    INSERT INTO knowledge_entities(id, entity_type, name, company_symbol, properties, updated_at)
                    VALUES (?, ?, ?, ?, ?, now())
                    ON CONFLICT (id)
                    DO UPDATE SET name = EXCLUDED.name,
                                  properties = EXCLUDED.properties,
                                  updated_at = now()
                    """,
                    entity.id(),
                    entity.type().name(),
                    entity.name(),
                    entity.companySymbol(),
                    jsonColumnMapper.jsonb(entity.properties())
            );
        }
        for (KnowledgeRelation relation : relations) {
            String company = relation.properties().getOrDefault("companySymbol", companySymbol);
            jdbcTemplate.update("""
                    INSERT INTO knowledge_relations(
                        id, company_symbol, source_entity_id, target_entity_id, relation_type, evidence_id, properties
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    relation.id(),
                    company,
                    relation.sourceEntityId(),
                    relation.targetEntityId(),
                    relation.relationType(),
                    relation.evidenceId(),
                    jsonColumnMapper.jsonb(relation.properties())
            );
        }
    }

    @Override
    public List<KnowledgeEntity> findEntities(String companySymbol) {
        return jdbcTemplate.query("""
                SELECT id, entity_type, name, company_symbol, properties::text
                FROM knowledge_entities
                WHERE company_symbol = ?
                ORDER BY entity_type, name
                """, (rs, rowNum) -> new KnowledgeEntity(
                rs.getString("id"),
                EntityType.valueOf(rs.getString("entity_type")),
                rs.getString("name"),
                rs.getString("company_symbol"),
                jsonColumnMapper.stringMap(rs.getString("properties"))
        ), companySymbol);
    }

    @Override
    public List<KnowledgeRelation> findRelations(String companySymbol) {
        return jdbcTemplate.query("""
                SELECT id, source_entity_id, target_entity_id, relation_type, evidence_id, properties::text
                FROM knowledge_relations
                WHERE company_symbol = ?
                ORDER BY relation_type, id
                """, (rs, rowNum) -> new KnowledgeRelation(
                rs.getString("id"),
                rs.getString("source_entity_id"),
                rs.getString("target_entity_id"),
                rs.getString("relation_type"),
                rs.getString("evidence_id"),
                jsonColumnMapper.stringMap(rs.getString("properties"))
        ), companySymbol);
    }
}

