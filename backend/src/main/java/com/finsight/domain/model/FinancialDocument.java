package com.finsight.domain.model;

import java.time.LocalDate;
import java.util.Map;

public record FinancialDocument(
        String id,
        String companySymbol,
        DocumentType type,
        String title,
        LocalDate publishedAt,
        String sourceUrl,
        String content,
        Map<String, String> metadata
) {
}

