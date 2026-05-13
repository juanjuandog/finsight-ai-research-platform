package com.finsight.infrastructure;

import com.finsight.domain.model.Company;
import com.finsight.domain.model.UserWatchlistItem;
import com.finsight.domain.repository.CompanyRepository;
import com.finsight.domain.repository.UserWatchlistRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryUserWatchlistRepository implements UserWatchlistRepository {
    private final CompanyRepository companyRepository;
    private final ConcurrentHashMap<String, Instant> watchlists = new ConcurrentHashMap<>();

    public InMemoryUserWatchlistRepository(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Override
    public void add(String userId, String companySymbol) {
        watchlists.put(key(userId, companySymbol), Instant.now());
    }

    @Override
    public void remove(String userId, String companySymbol) {
        watchlists.remove(key(userId, companySymbol));
    }

    @Override
    public List<UserWatchlistItem> findByUserId(String userId) {
        String prefix = userId + ":";
        return watchlists.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .sorted(Map.Entry.<String, Instant>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> toItem(userId, entry.getKey().substring(prefix.length()), entry.getValue()))
                .toList();
    }

    private UserWatchlistItem toItem(String userId, String symbol, Instant createdAt) {
        Company company = companyRepository.findBySymbol(symbol)
                .orElse(new Company(symbol, "股票 " + symbol, "CN", "待分类"));
        return new UserWatchlistItem(userId, company, createdAt);
    }

    private String key(String userId, String companySymbol) {
        return userId + ":" + companySymbol;
    }
}
