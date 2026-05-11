package com.finsight.infrastructure;

import com.finsight.domain.model.FinancialDocument;
import com.finsight.domain.repository.DocumentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryDocumentRepository implements DocumentRepository {
    private final ConcurrentHashMap<String, FinancialDocument> documents = new ConcurrentHashMap<>();

    @Override
    public void save(FinancialDocument document) {
        documents.put(document.id(), document);
    }

    @Override
    public Optional<FinancialDocument> findById(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    @Override
    public List<FinancialDocument> findByCompanySymbol(String companySymbol) {
        return documents.values().stream()
                .filter(document -> document.companySymbol().equals(companySymbol))
                .sorted(Comparator.comparing(FinancialDocument::publishedAt).reversed())
                .toList();
    }

    @Override
    public List<FinancialDocument> search(String companySymbol, String query, int limit) {
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return documents.values().stream()
                .filter(document -> companySymbol == null || document.companySymbol().equals(companySymbol))
                .filter(document -> normalizedQuery.isBlank()
                        || document.title().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        || document.content().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparing(FinancialDocument::publishedAt).reversed())
                .limit(limit)
                .toList();
    }
}
