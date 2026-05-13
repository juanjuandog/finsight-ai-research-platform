package com.finsight.domain.repository;

import com.finsight.domain.model.Company;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository {
    void save(Company company);

    Optional<Company> findBySymbol(String symbol);

    List<Company> findAll();

    List<Company> search(String query, int limit);

    long count();
}
