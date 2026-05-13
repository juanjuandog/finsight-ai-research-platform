ALTER TABLE workflow_tasks
    ADD COLUMN IF NOT EXISTS stage VARCHAR(64) NOT NULL DEFAULT 'CREATED',
    ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(256),
    ADD COLUMN IF NOT EXISTS fencing_token BIGINT;

CREATE INDEX IF NOT EXISTS idx_workflow_tasks_status_updated
    ON workflow_tasks(status, updated_at ASC);

ALTER TABLE stock_analysis_reports
    ADD COLUMN IF NOT EXISTS report_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS data_snapshot_hash VARCHAR(128);

UPDATE stock_analysis_reports
SET data_snapshot_hash = context_hash
WHERE data_snapshot_hash IS NULL;

ALTER TABLE stock_analysis_reports
    ALTER COLUMN data_snapshot_hash SET NOT NULL;

WITH ranked AS (
    SELECT id,
           row_number() OVER (PARTITION BY company_symbol ORDER BY generated_at ASC, id ASC) AS version
    FROM stock_analysis_reports
)
UPDATE stock_analysis_reports reports
SET report_version = ranked.version
FROM ranked
WHERE reports.id = ranked.id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_stock_analysis_reports_version
    ON stock_analysis_reports(company_symbol, report_version);

CREATE INDEX IF NOT EXISTS idx_stock_analysis_reports_snapshot
    ON stock_analysis_reports(company_symbol, data_snapshot_hash);
