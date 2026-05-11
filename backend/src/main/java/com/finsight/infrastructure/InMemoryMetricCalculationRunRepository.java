package com.finsight.infrastructure;

import com.finsight.domain.model.MetricCalculationRun;
import com.finsight.domain.repository.MetricCalculationRunRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@Profile("!postgres")
public class InMemoryMetricCalculationRunRepository implements MetricCalculationRunRepository {
    private final CopyOnWriteArrayList<MetricCalculationRun> runs = new CopyOnWriteArrayList<>();

    @Override
    public void save(MetricCalculationRun run) {
        runs.removeIf(existing -> existing.id().equals(run.id()));
        runs.add(run);
    }

    @Override
    public List<MetricCalculationRun> findByCompanySymbol(String companySymbol) {
        return runs.stream()
                .filter(run -> run.companySymbol().equals(companySymbol))
                .sorted(Comparator.comparing(MetricCalculationRun::finishedAt).reversed())
                .toList();
    }
}

