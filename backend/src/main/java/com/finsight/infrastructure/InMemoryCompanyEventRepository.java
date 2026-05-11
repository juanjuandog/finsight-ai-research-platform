package com.finsight.infrastructure;

import com.finsight.domain.model.CompanyEvent;
import com.finsight.domain.repository.CompanyEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@Profile("!postgres")
public class InMemoryCompanyEventRepository implements CompanyEventRepository {
    private final CopyOnWriteArrayList<CompanyEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void replaceCompanyEvents(String companySymbol, List<CompanyEvent> newEvents) {
        events.removeIf(event -> event.companySymbol().equals(companySymbol));
        events.addAll(newEvents);
    }

    @Override
    public List<CompanyEvent> findByCompanySymbol(String companySymbol) {
        return events.stream()
                .filter(event -> event.companySymbol().equals(companySymbol))
                .sorted(Comparator.comparing(CompanyEvent::happenedAt).reversed())
                .toList();
    }
}

