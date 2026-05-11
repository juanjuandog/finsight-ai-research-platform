package com.finsight.domain.repository;

import com.finsight.domain.model.RagTrace;

public interface RagTraceRepository {
    void save(String companySymbol, String question, RagTrace trace);
}

