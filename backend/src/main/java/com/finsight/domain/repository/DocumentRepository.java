package com.finsight.domain.repository;

import com.finsight.domain.model.FinancialDocument;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository {
    void save(FinancialDocument document);

    Optional<FinancialDocument> findById(String id);

    List<FinancialDocument> findByCompanySymbol(String companySymbol);

    List<FinancialDocument> search(String companySymbol, String query, int limit);
}

