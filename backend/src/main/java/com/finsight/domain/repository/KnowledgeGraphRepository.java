package com.finsight.domain.repository;

import com.finsight.domain.model.KnowledgeEntity;
import com.finsight.domain.model.KnowledgeRelation;

import java.util.List;

public interface KnowledgeGraphRepository {
    void replaceCompanyGraph(String companySymbol, List<KnowledgeEntity> entities, List<KnowledgeRelation> relations);

    List<KnowledgeEntity> findEntities(String companySymbol);

    List<KnowledgeRelation> findRelations(String companySymbol);
}

