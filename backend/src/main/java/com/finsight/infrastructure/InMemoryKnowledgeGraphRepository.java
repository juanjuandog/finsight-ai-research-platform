package com.finsight.infrastructure;

import com.finsight.domain.model.KnowledgeEntity;
import com.finsight.domain.model.KnowledgeRelation;
import com.finsight.domain.repository.KnowledgeGraphRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@Profile("!postgres")
public class InMemoryKnowledgeGraphRepository implements KnowledgeGraphRepository {
    private final CopyOnWriteArrayList<KnowledgeEntity> entities = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<KnowledgeRelation> relations = new CopyOnWriteArrayList<>();

    @Override
    public void replaceCompanyGraph(String companySymbol, List<KnowledgeEntity> newEntities, List<KnowledgeRelation> newRelations) {
        entities.removeIf(entity -> entity.companySymbol().equals(companySymbol));
        relations.removeIf(relation -> relation.properties().getOrDefault("companySymbol", "").equals(companySymbol));
        entities.addAll(newEntities);
        relations.addAll(newRelations);
    }

    @Override
    public List<KnowledgeEntity> findEntities(String companySymbol) {
        return entities.stream()
                .filter(entity -> entity.companySymbol().equals(companySymbol))
                .toList();
    }

    @Override
    public List<KnowledgeRelation> findRelations(String companySymbol) {
        return relations.stream()
                .filter(relation -> relation.properties().getOrDefault("companySymbol", "").equals(companySymbol))
                .toList();
    }
}

