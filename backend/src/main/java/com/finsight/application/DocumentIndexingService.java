package com.finsight.application;

import com.finsight.domain.model.DocumentChunk;
import com.finsight.domain.model.FinancialDocument;
import com.finsight.domain.repository.DocumentChunkRepository;
import com.finsight.domain.repository.DocumentRepository;
import com.finsight.rag.DocumentChunker;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentIndexingService {
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentChunker documentChunker;

    public DocumentIndexingService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            DocumentChunker documentChunker
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.documentChunker = documentChunker;
    }

    public IndexingResult indexCompany(String companySymbol) {
        List<FinancialDocument> documents = documentRepository.findByCompanySymbol(companySymbol);
        int chunkCount = 0;
        for (FinancialDocument document : documents) {
            List<DocumentChunk> chunks = documentChunker.chunk(document);
            chunkRepository.replaceChunks(document.id(), chunks);
            chunkCount += chunks.size();
        }
        return new IndexingResult(companySymbol, documents.size(), chunkCount);
    }

    public long countChunks(String companySymbol) {
        return chunkRepository.countByCompanySymbol(companySymbol);
    }

    public record IndexingResult(String companySymbol, int documentCount, int chunkCount) {
    }
}

