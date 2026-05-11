package com.finsight.api;

import com.finsight.application.MetricApplicationService;
import com.finsight.domain.model.FinancialMetric;
import com.finsight.domain.model.MetricCalculationRun;
import com.finsight.domain.model.RiskSignal;
import com.finsight.domain.repository.MetricRepository;
import com.finsight.metrics.MetricCalculationPlan;
import com.finsight.metrics.MetricDefinitionCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metrics")
public class MetricController {
    private final MetricApplicationService metricApplicationService;
    private final MetricRepository metricRepository;
    private final MetricDefinitionCatalog definitionCatalog;

    public MetricController(
            MetricApplicationService metricApplicationService,
            MetricRepository metricRepository,
            MetricDefinitionCatalog definitionCatalog
    ) {
        this.metricApplicationService = metricApplicationService;
        this.metricRepository = metricRepository;
        this.definitionCatalog = definitionCatalog;
    }

    @PostMapping("/recalculate/{companySymbol}")
    public MetricApplicationService.MetricCalculationResult recalculate(@PathVariable String companySymbol) {
        return metricApplicationService.recalculate(companySymbol);
    }

    @GetMapping("/{companySymbol}")
    public List<FinancialMetric> metrics(@PathVariable String companySymbol) {
        return metricRepository.findMetrics(companySymbol);
    }

    @GetMapping("/{companySymbol}/risks")
    public List<RiskSignal> risks(@PathVariable String companySymbol) {
        return metricRepository.findRiskSignals(companySymbol);
    }

    @GetMapping("/{companySymbol}/runs")
    public List<MetricCalculationRun> runs(@PathVariable String companySymbol) {
        return metricApplicationService.runs(companySymbol);
    }

    @GetMapping("/definitions")
    public MetricCalculationPlan definitions() {
        return definitionCatalog.corePlan();
    }
}
