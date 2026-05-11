package com.finsight.infrastructure;

import com.finsight.domain.model.FinancialStatement;
import com.finsight.domain.repository.FinancialStatementRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@Profile("!postgres")
public class InMemoryFinancialStatementRepository implements FinancialStatementRepository {
    private final CopyOnWriteArrayList<FinancialStatement> statements = new CopyOnWriteArrayList<>();

    @Override
    public void save(FinancialStatement statement) {
        statements.removeIf(existing -> existing.companySymbol().equals(statement.companySymbol())
                && existing.fiscalYear().equals(statement.fiscalYear()));
        statements.add(statement);
    }

    @Override
    public List<FinancialStatement> findByCompanySymbol(String companySymbol) {
        return statements.stream()
                .filter(statement -> statement.companySymbol().equals(companySymbol))
                .sorted(Comparator.comparing(FinancialStatement::fiscalYear))
                .toList();
    }
}
