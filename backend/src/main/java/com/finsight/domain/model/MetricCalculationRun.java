package com.finsight.domain.model;

import java.time.Instant;
import java.util.Map;

public record MetricCalculationRun(
        String id,
        String companySymbol,
        String planVersion,
        int statementCount,
        int metricCount,
        int riskSignalCount,
        Instant startedAt,
        Instant finishedAt,
        Map<String, Object> metadata
) {
}

