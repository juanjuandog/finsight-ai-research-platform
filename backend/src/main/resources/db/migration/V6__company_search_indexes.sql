CREATE INDEX IF NOT EXISTS idx_companies_name
    ON companies(name);

CREATE INDEX IF NOT EXISTS idx_companies_exchange_industry
    ON companies(exchange, industry);
