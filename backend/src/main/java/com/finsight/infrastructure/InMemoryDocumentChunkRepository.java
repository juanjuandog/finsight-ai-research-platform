package com.finsight.infrastructure;

import com.finsight.domain.model.DocumentChunk;
import com.finsight.domain.repository.DocumentChunkRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@Profile("!postgres")
public class InMemoryDocumentChunkRepository implements DocumentChunkRepository {
    private final CopyOnWriteArrayList<DocumentChunk> chunks = new CopyOnWriteArrayList<>();

    @Override
    public void replaceChunks(String documentId, List<DocumentChunk> newChunks) {
        chunks.removeIf(chunk -> chunk.documentId().equals(documentId));
        chunks.addAll(newChunks);
    }

    @Override
    public List<DocumentChunk> findByDocumentId(String documentId) {
        return chunks.stream()
                .filter(chunk -> chunk.documentId().equals(documentId))
                .sorted(Comparator.comparing(DocumentChunk::chunkIndex))
                .toList();
    }

    @Override
    public List<DocumentChunk> keywordSearch(String companySymbol, String query, int limit) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return chunks.stream()
                .filter(chunk -> companySymbol == null || chunk.companySymbol().equals(companySymbol))
                .filter(chunk -> normalized.isBlank()
                        || chunk.title().toLowerCase(Locale.ROOT).contains(normalized)
                        || chunk.section().toLowerCase(Locale.ROOT).contains(normalized)
                        || chunk.text().toLowerCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator.comparing(DocumentChunk::publishedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<DocumentChunk> vectorSearch(String companySymbol, List<Double> embedding, int limit) {
        return chunks.stream()
                .filter(chunk -> companySymbol == null || chunk.companySymbol().equals(companySymbol))
                .sorted(Comparator.comparingDouble(chunk -> -cosine(chunk.embedding(), embedding)))
                .limit(limit)
                .toList();
    }

    @Override
    public long countByCompanySymbol(String companySymbol) {
        return chunks.stream()
                .filter(chunk -> chunk.companySymbol().equals(companySymbol))
                .count();
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int size = Math.min(left.size(), right.size());
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < size; i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}

