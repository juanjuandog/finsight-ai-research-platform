package com.finsight.infrastructure;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.RiskSignal;
import com.finsight.domain.repository.MetricRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
@Profile("!postgres")
public class InMemoryMetricRepository implements MetricRepository {
    private final CopyOnWriteArrayList<FinancialMetric> metrics = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<RiskSignal> riskSignals = new CopyOnWriteArrayList<>();

    @Override
    public void saveMetric(FinancialMetric metric) {
        metrics.removeIf(existing -> existing.companySymbol().equals(metric.companySymbol())
                && existing.fiscalYear().equals(metric.fiscalYear())
                && existing.code().equals(metric.code()));
        metrics.add(metric);
    }

    @Override
    public void saveRiskSignal(RiskSignal riskSignal) {
        riskSignals.removeIf(existing -> existing.id().equals(riskSignal.id()));
        riskSignals.add(riskSignal);
    }

    @Override
    public List<FinancialMetric> findMetrics(String companySymbol) {
        return metrics.stream()
                .filter(metric -> metric.companySymbol().equals(companySymbol))
                .toList();
    }

    @Override
    public List<RiskSignal> findRiskSignals(String companySymbol) {
        return riskSignals.stream()
                .filter(signal -> signal.companySymbol().equals(companySymbol))
                .toList();
    }
}
