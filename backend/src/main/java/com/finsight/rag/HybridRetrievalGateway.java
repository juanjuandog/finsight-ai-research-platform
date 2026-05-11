package com.finsight.rag;

import com.finsight.domain.model.DocumentChunk;
import com.finsight.domain.repository.DocumentChunkRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HybridRetrievalGateway {
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;

    public HybridRetrievalGateway(DocumentChunkRepository chunkRepository, EmbeddingService embeddingService) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
    }

    public List<RetrievalHit> search(String companySymbol, String question, int limit) {
        List<RetrievalHit> hits = new ArrayList<>();
        List<DocumentChunk> keywordChunks = chunkRepository.keywordSearch(companySymbol, question, limit);
        for (int i = 0; i < keywordChunks.size(); i++) {
            hits.add(new RetrievalHit(keywordChunks.get(i), 1.0 - i * 0.04, "keyword"));
        }

        List<Double> queryEmbedding = embeddingService.embed(question);
        List<DocumentChunk> vectorChunks = chunkRepository.vectorSearch(companySymbol, queryEmbedding, limit);
        for (int i = 0; i < vectorChunks.size(); i++) {
            hits.add(new RetrievalHit(vectorChunks.get(i), 0.86 - i * 0.03, "vector"));
        }

        Map<String, RetrievalHit> merged = new LinkedHashMap<>();
        for (RetrievalHit hit : hits) {
            merged.merge(hit.chunk().id(), hit, (left, right) -> new RetrievalHit(
                    left.chunk(),
                    Math.max(left.score(), right.score()) + 0.08,
                    left.channel().equals(right.channel()) ? left.channel() : left.channel() + "+" + right.channel()
            ));
        }

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
                .limit(limit)
                .toList();
    }
}

