package com.finsight.domain.repository;

import com.finsight.domain.model.MetricCalculationRun;

import java.util.List;

public interface MetricCalculationRunRepository {
    void save(MetricCalculationRun run);

    List<MetricCalculationRun> findByCompanySymbol(String companySymbol);
}

