CREATE TABLE IF NOT EXISTS metric_calculation_runs (
    id VARCHAR(128) PRIMARY KEY,
    company_symbol VARCHAR(32) NOT NULL REFERENCES companies(symbol),
    plan_version VARCHAR(64) NOT NULL,
    statement_count INTEGER NOT NULL,
    metric_count INTEGER NOT NULL,
    risk_signal_count INTEGER NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_metric_runs_company
    ON metric_calculation_runs(company_symbol, finished_at DESC);

