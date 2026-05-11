package com.finsight.ai;

import com.finsight.domain.model.EvidenceChunk;

import java.util.List;
import java.util.Map;

public interface AiServiceClient {
    List<EvidenceChunk> rerank(String question, List<EvidenceChunk> candidates);

    String generateAnswer(String question, Map<String, Object> structuredQuery, List<EvidenceChunk> evidence);
}

