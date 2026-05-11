package com.finsight.metrics;

import java.util.List;

public record MetricCalculationPlan(
        String version,
        List<MetricDefinition> definitions
) {
}

