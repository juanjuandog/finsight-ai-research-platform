package com.finsight.domain.repository;

import com.finsight.domain.model.FinancialStatement;

import java.util.List;

public interface FinancialStatementRepository {
    void save(FinancialStatement statement);

    List<FinancialStatement> findByCompanySymbol(String companySymbol);
}

