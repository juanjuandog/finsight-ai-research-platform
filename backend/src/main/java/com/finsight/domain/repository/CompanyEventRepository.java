package com.finsight.domain.repository;

import com.finsight.domain.model.CompanyEvent;

import java.util.List;

public interface CompanyEventRepository {
    void replaceCompanyEvents(String companySymbol, List<CompanyEvent> events);

    List<CompanyEvent> findByCompanySymbol(String companySymbol);
}

