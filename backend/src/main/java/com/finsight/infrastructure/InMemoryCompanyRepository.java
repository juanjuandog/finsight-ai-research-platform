package com.finsight.infrastructure;

import com.finsight.domain.model.Company;
import com.finsight.domain.repository.CompanyRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryCompanyRepository implements CompanyRepository {
    private final ConcurrentHashMap<String, Company> companies = new ConcurrentHashMap<>();

    @Override
    public void save(Company company) {
        companies.put(company.symbol(), company);
    }

    @Override
    public Optional<Company> findBySymbol(String symbol) {
        return Optional.ofNullable(companies.get(symbol));
    }

    @Override
    public List<Company> findAll() {
        return companies.values().stream()
                .sorted(Comparator.comparing(Company::symbol))
                .toList();
    }

    @Override
    public List<Company> search(String query, int limit) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return companies.values().stream()
                .filter(company -> normalized.isBlank()
                        || company.symbol().toLowerCase(Locale.ROOT).contains(normalized)
                        || company.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || company.exchange().toLowerCase(Locale.ROOT).contains(normalized)
                        || company.industry().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing(Company::symbol))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public long count() {
        return companies.size();
    }
}
