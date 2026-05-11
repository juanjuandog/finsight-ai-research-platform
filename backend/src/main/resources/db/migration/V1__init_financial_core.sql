CREATE TABLE IF NOT EXISTS companies (
    symbol VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    exchange VARCHAR(32) NOT NULL,
    industry VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS financial_documents (
    id VARCHAR(128) PRIMARY KEY,
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    document_type VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    published_at DATE NOT NULL,
    source_url TEXT NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_hash VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_documents_company_published
    ON financial_documents(company_symbol, published_at DESC);

CREATE INDEX IF NOT EXISTS idx_documents_metadata
    ON financial_documents USING GIN(metadata);

CREATE INDEX IF NOT EXISTS idx_documents_content_tsv
    ON financial_documents USING GIN(to_tsvector('simple', title || ' ' || content));

CREATE TABLE IF NOT EXISTS financial_statements (
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    fiscal_year INTEGER NOT NULL,
    revenue NUMERIC(24, 4) NOT NULL,
    gross_profit NUMERIC(24, 4) NOT NULL,
    net_profit NUMERIC(24, 4) NOT NULL,
    operating_cash_flow NUMERIC(24, 4) NOT NULL,
    total_assets NUMERIC(24, 4) NOT NULL,
    total_liabilities NUMERIC(24, 4) NOT NULL,
    equity NUMERIC(24, 4) NOT NULL,
    accounts_receivable NUMERIC(24, 4) NOT NULL,
    source_document_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_symbol, fiscal_year)
);

CREATE TABLE IF NOT EXISTS financial_metrics (
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    fiscal_year INTEGER NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    value NUMERIC(24, 8) NOT NULL,
    formula_version VARCHAR(64) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_symbol, fiscal_year, code, formula_version)
);

CREATE INDEX IF NOT EXISTS idx_metrics_company_code
    ON financial_metrics(company_symbol, code, fiscal_year DESC);

CREATE TABLE IF NOT EXISTS risk_signals (
    id VARCHAR(128) PRIMARY KEY,
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    code VARCHAR(64) NOT NULL,
    title VARCHAR(256) NOT NULL,
    explanation TEXT NOT NULL,
    severity INTEGER NOT NULL,
    detected_at DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_risk_signals_company
    ON risk_signals(company_symbol, detected_at DESC);

CREATE TABLE IF NOT EXISTS workflow_tasks (
    id VARCHAR(128) PRIMARY KEY,
    task_type VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL UNIQUE,
    status VARCHAR(64) NOT NULL,
    attempts INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_workflow_tasks_status
    ON workflow_tasks(status, created_at DESC);

CREATE TABLE IF NOT EXISTS company_events (
    id VARCHAR(128) PRIMARY KEY,
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    event_type VARCHAR(64) NOT NULL,
    happened_at DATE NOT NULL,
    title VARCHAR(256) NOT NULL,
    summary TEXT NOT NULL,
    evidence_document_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_company_events_company_time
    ON company_events(company_symbol, happened_at DESC);

CREATE TABLE IF NOT EXISTS rag_traces (
    trace_id VARCHAR(128) PRIMARY KEY,
    company_symbol VARCHAR(32),
    question TEXT NOT NULL,
    structured_query JSONB NOT NULL DEFAULT '{}'::jsonb,
    retrieval_channels JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_count INTEGER NOT NULL,
    latency_millis BIGINT NOT NULL,
    model_name VARCHAR(128),
    prompt_version VARCHAR(64),
    token_cost NUMERIC(18, 8),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

