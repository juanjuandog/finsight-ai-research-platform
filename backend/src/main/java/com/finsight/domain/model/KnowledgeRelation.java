package com.finsight.domain.model;

import java.util.Map;

public record KnowledgeRelation(
        String id,
        String sourceEntityId,
        String targetEntityId,
        String relationType,
        String evidenceId,
        Map<String, String> properties
) {
}

