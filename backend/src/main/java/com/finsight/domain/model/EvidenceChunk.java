package com.finsight.domain.model;

import java.time.LocalDate;

public record EvidenceChunk(
        String documentId,
        String title,
        DocumentType documentType,
        LocalDate publishedAt,
        String section,
        String text,
        double score
) {
}

