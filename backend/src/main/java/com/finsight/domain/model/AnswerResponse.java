package com.finsight.domain.model;

import java.util.List;

public record AnswerResponse(
        String answer,
        List<EvidenceChunk> evidence,
        RagTrace trace
) {
}

