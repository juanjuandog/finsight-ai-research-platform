package com.finsight.domain.model;

import java.util.Map;

public record KnowledgeEntity(
        String id,
        EntityType type,
        String name,
        String companySymbol,
        Map<String, String> properties
) {
}

