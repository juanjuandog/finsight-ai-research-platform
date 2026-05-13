CREATE TABLE IF NOT EXISTS stock_analysis_reports (
    id VARCHAR(128) PRIMARY KEY,
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    rating VARCHAR(32) NOT NULL,
    summary TEXT NOT NULL,
    positive_points JSONB NOT NULL DEFAULT '[]'::jsonb,
    risk_points JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence INTEGER NOT NULL,
    citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    model VARCHAR(128) NOT NULL,
    source VARCHAR(128) NOT NULL,
    ai_generated BOOLEAN NOT NULL,
    context_hash VARCHAR(128) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_stock_analysis_reports_company_time
    ON stock_analysis_reports(company_symbol, generated_at DESC);

CREATE INDEX IF NOT EXISTS idx_stock_analysis_reports_context
    ON stock_analysis_reports(company_symbol, context_hash);

CREATE TABLE IF NOT EXISTS user_watchlists (
    user_id VARCHAR(128) NOT NULL,
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, company_symbol)
);

CREATE INDEX IF NOT EXISTS idx_user_watchlists_user_time
    ON user_watchlists(user_id, created_at DESC);
