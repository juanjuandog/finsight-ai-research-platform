package com.finsight.application;

import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.FinancialStatement;
import com.finsight.domain.model.MetricCalculationRun;
import com.finsight.domain.model.RiskSignal;
import com.finsight.domain.repository.FinancialStatementRepository;
import com.finsight.domain.repository.MetricCalculationRunRepository;
import com.finsight.domain.repository.MetricRepository;
import com.finsight.metrics.FinancialMetricCalculator;
import com.finsight.metrics.MetricDefinitionCatalog;
import com.finsight.metrics.RiskSignalDetector;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MetricApplicationService {
    private final FinancialStatementRepository statementRepository;
    private final MetricRepository metricRepository;
    private final MetricCalculationRunRepository runRepository;
    private final FinancialMetricCalculator metricCalculator;
    private final RiskSignalDetector riskSignalDetector;
    private final MetricDefinitionCatalog definitionCatalog;

    public MetricApplicationService(
            FinancialStatementRepository statementRepository,
            MetricRepository metricRepository,
            MetricCalculationRunRepository runRepository,
            FinancialMetricCalculator metricCalculator,
            RiskSignalDetector riskSignalDetector,
            MetricDefinitionCatalog definitionCatalog
    ) {
        this.statementRepository = statementRepository;
        this.metricRepository = metricRepository;
        this.runRepository = runRepository;
        this.metricCalculator = metricCalculator;
        this.riskSignalDetector = riskSignalDetector;
        this.definitionCatalog = definitionCatalog;
    }

    public MetricCalculationResult recalculate(String companySymbol) {
        Instant startedAt = Instant.now();
        List<FinancialStatement> statements = statementRepository.findByCompanySymbol(companySymbol);
        List<FinancialMetric> metrics = metricCalculator.calculate(statements);
        metrics.forEach(metricRepository::saveMetric);
        List<RiskSignal> signals = riskSignalDetector.detect(companySymbol, metrics);
        signals.forEach(metricRepository::saveRiskSignal);
        MetricCalculationRun run = new MetricCalculationRun(
                UUID.randomUUID().toString(),
                companySymbol,
                definitionCatalog.corePlan().version(),
                statements.size(),
                metrics.size(),
                signals.size(),
                startedAt,
                Instant.now(),
                Map.of(
                        "metricDefinitionCount", definitionCatalog.corePlan().definitions().size(),
                        "calculationMode", "full-rebuild"
                )
        );
        runRepository.save(run);
        return new MetricCalculationResult(metrics, signals, run);
    }

    public List<MetricCalculationRun> runs(String companySymbol) {
        return runRepository.findByCompanySymbol(companySymbol);
    }

    public record MetricCalculationResult(
            List<FinancialMetric> metrics,
            List<RiskSignal> riskSignals,
            MetricCalculationRun run
    ) {
    }
}
