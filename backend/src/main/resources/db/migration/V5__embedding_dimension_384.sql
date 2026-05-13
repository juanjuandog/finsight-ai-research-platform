DROP INDEX IF EXISTS idx_document_chunks_embedding;

ALTER TABLE document_chunks
    ALTER COLUMN embedding TYPE vector(384)
    USING NULL::vector(384);

CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding
    ON document_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 32);
