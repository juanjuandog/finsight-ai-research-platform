package com.finsight.infrastructure;

import com.finsight.domain.model.RagTrace;
import com.finsight.domain.repository.RagTraceRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryRagTraceRepository implements RagTraceRepository {
    @Override
    public void save(String companySymbol, String question, RagTrace trace) {
    }
}

