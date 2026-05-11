package com.finsight.domain.model;

import java.time.LocalDate;
import java.util.List;

public record CompanyEvent(
        String id,
        String companySymbol,
        EventType type,
        LocalDate happenedAt,
        String title,
        String summary,
        List<String> evidenceDocumentIds
) {
}

