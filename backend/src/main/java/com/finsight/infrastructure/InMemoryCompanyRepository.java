package com.finsight.infrastructure;

import com.finsight.domain.model.Company;
import com.finsight.domain.repository.CompanyRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
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
        return new ArrayList<>(companies.values());
    }
}
