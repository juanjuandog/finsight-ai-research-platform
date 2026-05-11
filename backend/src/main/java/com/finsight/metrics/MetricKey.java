package com.finsight.metrics;

import java.time.Year;

public record MetricKey(
        Year fiscalYear,
        String code
) {
    public MetricKey previousYear() {
        return new MetricKey(fiscalYear.minusYears(1), code);
    }
}

