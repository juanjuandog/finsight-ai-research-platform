package com.finsight.rag;

import com.finsight.domain.model.DocumentChunk;

public record RetrievalHit(
        DocumentChunk chunk,
        double score,
        String channel
) {
}

