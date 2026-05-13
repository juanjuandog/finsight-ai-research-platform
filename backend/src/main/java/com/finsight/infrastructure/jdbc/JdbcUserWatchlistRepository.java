package com.finsight.infrastructure.jdbc;

import com.finsight.domain.model.Company;
import com.finsight.domain.model.UserWatchlistItem;
import com.finsight.domain.repository.UserWatchlistRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("postgres")
public class JdbcUserWatchlistRepository implements UserWatchlistRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserWatchlistRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void add(String userId, String companySymbol) {
        jdbcTemplate.update("""
                INSERT INTO user_watchlists(user_id, company_symbol)
                VALUES (?, ?)
                ON CONFLICT (user_id, company_symbol) DO NOTHING
                """, userId, companySymbol);
    }

    @Override
    public void remove(String userId, String companySymbol) {
        jdbcTemplate.update("""
                DELETE FROM user_watchlists
                WHERE user_id = ? AND company_symbol = ?
                """, userId, companySymbol);
    }

    @Override
    public List<UserWatchlistItem> findByUserId(String userId) {
        return jdbcTemplate.query("""
                SELECT w.user_id, w.created_at, c.symbol, c.name, c.exchange, c.industry
                FROM user_watchlists w
                JOIN companies c ON c.symbol = w.company_symbol
                WHERE w.user_id = ?
                ORDER BY w.created_at DESC
                """, (rs, rowNum) -> new UserWatchlistItem(
                rs.getString("user_id"),
                new Company(
                        rs.getString("symbol"),
                        rs.getString("name"),
                        rs.getString("exchange"),
                        rs.getString("industry")
                ),
                rs.getTimestamp("created_at").toInstant()
        ), userId);
    }
}
