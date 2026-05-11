package com.finsight.domain.repository;

import com.finsight.domain.model.DocumentChunk;

import java.util.List;

public interface DocumentChunkRepository {
    void replaceChunks(String documentId, List<DocumentChunk> chunks);

    List<DocumentChunk> findByDocumentId(String documentId);

    List<DocumentChunk> keywordSearch(String companySymbol, String query, int limit);

    List<DocumentChunk> vectorSearch(String companySymbol, List<Double> embedding, int limit);

    long countByCompanySymbol(String companySymbol);
}

