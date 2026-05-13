package com.finsight.infrastructure.jdbc;

import com.finsight.domain.model.Company;
import com.finsight.domain.repository.CompanyRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("postgres")
public class JdbcCompanyRepository implements CompanyRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcCompanyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Company company) {
        jdbcTemplate.update("""
                INSERT INTO companies(symbol, name, exchange, industry, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (symbol)
                DO UPDATE SET name = EXCLUDED.name,
                              exchange = EXCLUDED.exchange,
                              industry = EXCLUDED.industry,
                              updated_at = now()
                """, company.symbol(), company.name(), company.exchange(), company.industry());
    }

    @Override
    public Optional<Company> findBySymbol(String symbol) {
        List<Company> result = jdbcTemplate.query("""
                SELECT symbol, name, exchange, industry
                FROM companies
                WHERE symbol = ?
                """, (rs, rowNum) -> new Company(
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getString("exchange"),
                rs.getString("industry")
        ), symbol);
        return result.stream().findFirst();
    }

    @Override
    public List<Company> findAll() {
        return jdbcTemplate.query("""
                SELECT symbol, name, exchange, industry
                FROM companies
                ORDER BY symbol
                """, (rs, rowNum) -> new Company(
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getString("exchange"),
                rs.getString("industry")
        ));
    }

    @Override
    public List<Company> search(String query, int limit) {
        String normalized = query == null ? "" : query.trim();
        String like = "%" + normalized + "%";
        return jdbcTemplate.query("""
                SELECT symbol, name, exchange, industry
                FROM companies
                WHERE ? = ''
                   OR symbol ILIKE ?
                   OR name ILIKE ?
                   OR exchange ILIKE ?
                   OR industry ILIKE ?
                ORDER BY symbol
                LIMIT ?
                """, (rs, rowNum) -> new Company(
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getString("exchange"),
                rs.getString("industry")
        ), normalized, like, like, like, like, Math.max(1, limit));
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM companies", Long.class);
        return count == null ? 0 : count;
    }
}
