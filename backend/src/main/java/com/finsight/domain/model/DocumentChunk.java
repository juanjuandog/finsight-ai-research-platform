package com.finsight.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DocumentChunk(
        String id,
        String documentId,
        String companySymbol,
        DocumentType documentType,
        String title,
        LocalDate publishedAt,
        String section,
        int chunkIndex,
        String text,
        String contentHash,
        List<Double> embedding,
        Map<String, String> metadata
) {
}

