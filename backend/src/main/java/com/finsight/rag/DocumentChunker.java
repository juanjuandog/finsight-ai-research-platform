package com.finsight.rag;

import com.finsight.domain.model.DocumentChunk;
import com.finsight.domain.model.FinancialDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DocumentChunker {
    private static final int TARGET_CHARS = 220;
    private static final int OVERLAP_CHARS = 40;

    private final EmbeddingService embeddingService;

    public DocumentChunker(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public List<DocumentChunk> chunk(FinancialDocument document) {
        String normalized = document.content().replaceAll("\\s+", " ").trim();
        String section = document.metadata().getOrDefault("section", "正文");
        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + TARGET_CHARS);
            String text = normalized.substring(start, end);
            String chunkId = document.id() + "-chunk-" + index;
            chunks.add(new DocumentChunk(
                    chunkId,
                    document.id(),
                    document.companySymbol(),
                    document.type(),
                    document.title(),
                    document.publishedAt(),
                    section,
                    index,
                    text,
                    embeddingService.hash(document.id() + ":" + index + ":" + text),
                    embeddingService.embed(text),
                    Map.of(
                            "sourceUrl", document.sourceUrl(),
                            "section", section,
                            "chunkStrategy", "fixed-window-v1"
                    )
            ));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - OVERLAP_CHARS, start + 1);
            index++;
        }
        return chunks;
    }
}

