CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_chunks (
    id VARCHAR(128) PRIMARY KEY,
    document_id VARCHAR(128) NOT NULL REFERENCES financial_documents(id) ON DELETE CASCADE,
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    document_type VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    published_at DATE NOT NULL,
    section VARCHAR(256) NOT NULL,
    chunk_index INTEGER NOT NULL,
    text TEXT NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    embedding vector(16),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_document_chunks_company
    ON document_chunks(company_symbol, published_at DESC);

CREATE INDEX IF NOT EXISTS idx_document_chunks_metadata
    ON document_chunks USING GIN(metadata);

CREATE INDEX IF NOT EXISTS idx_document_chunks_text_tsv
    ON document_chunks USING GIN(to_tsvector('simple', title || ' ' || section || ' ' || text));

CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding
    ON document_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 32);

