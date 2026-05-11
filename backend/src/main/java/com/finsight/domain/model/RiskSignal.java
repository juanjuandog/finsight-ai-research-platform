package com.finsight.domain.model;

import java.time.LocalDate;

public record RiskSignal(
        String id,
        String companySymbol,
        String code,
        String title,
        String explanation,
        int severity,
        LocalDate detectedAt
) {
}

