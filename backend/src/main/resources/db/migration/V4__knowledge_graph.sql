CREATE TABLE IF NOT EXISTS knowledge_entities (
    id VARCHAR(128) PRIMARY KEY,
    entity_type VARCHAR(64) NOT NULL,
    name VARCHAR(256) NOT NULL,
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    properties JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_entities_company
    ON knowledge_entities(company_symbol, entity_type, name);

CREATE TABLE IF NOT EXISTS knowledge_relations (
    id VARCHAR(128) PRIMARY KEY,
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    source_entity_id VARCHAR(128) NOT NULL REFERENCES knowledge_entities(id) ON DELETE CASCADE,
    target_entity_id VARCHAR(128) NOT NULL REFERENCES knowledge_entities(id) ON DELETE CASCADE,
    relation_type VARCHAR(96) NOT NULL,
    evidence_id VARCHAR(128),
    properties JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_relations_company
    ON knowledge_relations(company_symbol, relation_type);

