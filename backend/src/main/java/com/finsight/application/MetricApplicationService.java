package com.finsight.application;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.FinancialStatement;
import com.finsight.domain.model.RiskSignal;
import com.finsight.domain.repository.FinancialStatementRepository;
import com.finsight.domain.repository.MetricRepository;
import com.finsight.metrics.FinancialMetricCalculator;
import com.finsight.metrics.RiskSignalDetector;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MetricApplicationService {
    private final FinancialStatementRepository statementRepository;
    private final MetricRepository metricRepository;
    private final FinancialMetricCalculator metricCalculator;
    private final RiskSignalDetector riskSignalDetector;

    public MetricApplicationService(
            FinancialStatementRepository statementRepository,
            MetricRepository metricRepository,
            FinancialMetricCalculator metricCalculator,
            RiskSignalDetector riskSignalDetector
    ) {
        this.statementRepository = statementRepository;
        this.metricRepository = metricRepository;
        this.metricCalculator = metricCalculator;
        this.riskSignalDetector = riskSignalDetector;
    }

    public MetricCalculationResult recalculate(String companySymbol) {
        List<FinancialStatement> statements = statementRepository.findByCompanySymbol(companySymbol);
        List<FinancialMetric> metrics = metricCalculator.calculate(statements);
        metrics.forEach(metricRepository::saveMetric);
        List<RiskSignal> signals = riskSignalDetector.detect(companySymbol, metrics);
        signals.forEach(metricRepository::saveRiskSignal);
        return new MetricCalculationResult(metrics, signals);
    }

    public record MetricCalculationResult(List<FinancialMetric> metrics, List<RiskSignal> riskSignals) {
    }
}

