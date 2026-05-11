package com.finsight.domain;

import com.finsight.domain.model.Company;
import com.finsight.domain.model.FinancialDocument;
import com.finsight.domain.model.FinancialStatement;

import java.util.List;

public interface FinancialDataSource {
    String sourceName();

    List<Company> fetchCompanies();

    List<FinancialDocument> fetchDocuments(String companySymbol);

    List<FinancialStatement> fetchStatements(String companySymbol);
}

