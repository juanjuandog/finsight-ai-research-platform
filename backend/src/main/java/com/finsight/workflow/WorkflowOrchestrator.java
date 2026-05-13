package com.finsight.workflow;

import com.finsight.application.MetricApplicationService;
import com.finsight.application.DocumentIndexingService;
import com.finsight.application.CompanyIntelligenceService;
import com.finsight.application.StockAiAnalysisService;
import com.finsight.domain.FinancialDataIngestionTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WorkflowOrchestrator {
    private final WorkflowTaskRepository taskRepository;
    private final FinancialDataIngestionTemplate dataSource;
    private final MetricApplicationService metricApplicationService;
    private final DocumentIndexingService documentIndexingService;
    private final CompanyIntelligenceService companyIntelligenceService;
    private final StockAiAnalysisService stockAiAnalysisService;
    private final MeterRegistry meterRegistry;

    public WorkflowOrchestrator(
            WorkflowTaskRepository taskRepository,
            FinancialDataIngestionTemplate dataSource,
            MetricApplicationService metricApplicationService,
            DocumentIndexingService documentIndexingService,
            CompanyIntelligenceService companyIntelligenceService,
            StockAiAnalysisService stockAiAnalysisService,
            MeterRegistry meterRegistry
    ) {
        this.taskRepository = taskRepository;
        this.dataSource = dataSource;
        this.metricApplicationService = metricApplicationService;
        this.documentIndexingService = documentIndexingService;
        this.companyIntelligenceService = companyIntelligenceService;
        this.stockAiAnalysisService = stockAiAnalysisService;
        this.meterRegistry = meterRegistry;
    }

    public void execute(String taskId) {
        WorkflowTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow task not found: " + taskId));
        if (task.status() == WorkflowStatus.SUCCEEDED) {
            return;
        }
        switch (task.taskType()) {
            case WorkflowTaskType.FINANCIAL_DATA_INGESTION -> {
                dataSource.executeIngestionTask(task);
                String companySymbol = stringPayload(task.payload(), "companySymbol");
                WorkflowTask metricTask = createOrReuseTask(
                        WorkflowTaskType.FINANCIAL_METRIC_RECALCULATION,
                        "metric:" + companySymbol + ":" + task.id(),
                        Map.of("companySymbol", companySymbol, "parentTaskId", task.id())
                );
                WorkflowTask indexTask = createOrReuseTask(
                        WorkflowTaskType.DOCUMENT_INDEX_BUILD,
                        "index:" + companySymbol + ":" + task.id(),
                        Map.of("companySymbol", companySymbol, "parentTaskId", task.id())
                );
                WorkflowTask intelligenceTask = createOrReuseTask(
                        WorkflowTaskType.COMPANY_INTELLIGENCE_BUILD,
                        "intelligence:" + companySymbol + ":" + task.id(),
                        Map.of("companySymbol", companySymbol, "parentTaskId", task.id())
                );
                WorkflowTask aiAnalysisTask = createOrReuseTask(
                        WorkflowTaskType.STOCK_AI_ANALYSIS,
                        "stock-ai-analysis:" + companySymbol + ":" + task.id(),
                        Map.of("companySymbol", companySymbol, "parentTaskId", task.id())
                );
                execute(metricTask.id());
                execute(indexTask.id());
                execute(intelligenceTask.id());
                execute(aiAnalysisTask.id());
            }
            case WorkflowTaskType.FINANCIAL_METRIC_RECALCULATION -> {
                executeTask(task, () -> metricApplicationService.recalculate(stringPayload(task.payload(), "companySymbol")));
            }
            case WorkflowTaskType.DOCUMENT_INDEX_BUILD -> {
                executeTask(task, () -> documentIndexingService.indexCompany(stringPayload(task.payload(), "companySymbol")));
            }
            case WorkflowTaskType.COMPANY_INTELLIGENCE_BUILD -> {
                executeTask(task, () -> companyIntelligenceService.rebuild(stringPayload(task.payload(), "companySymbol")));
            }
            case WorkflowTaskType.STOCK_AI_ANALYSIS -> {
                executeTask(task, () -> stockAiAnalysisService.analyze(stringPayload(task.payload(), "companySymbol")));
            }
            default -> throw new IllegalArgumentException("Unsupported workflow task type: " + task.taskType());
        }
    }

    private WorkflowTask createOrReuseTask(String taskType, String idempotencyKey, Map<String, Object> payload) {
        return taskRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> taskRepository.save(WorkflowTask.created(taskType, idempotencyKey, payload)));
    }

    private void executeTask(WorkflowTask task, Runnable runnable) {
        WorkflowTask runningTask = task.running();
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            taskRepository.save(runningTask);
            runnable.run();
            taskRepository.save(runningTask.succeeded());
            recordWorkflowMetric(task.taskType(), "succeeded", sample);
        } catch (RuntimeException ex) {
            taskRepository.save(runningTask.failed(ex.getMessage()));
            recordWorkflowMetric(task.taskType(), "failed", sample);
            throw ex;
        }
    }

    private void recordWorkflowMetric(String taskType, String result, Timer.Sample sample) {
        sample.stop(Timer.builder("finsight.workflow.task.duration")
                .description("Workflow task execution duration")
                .tag("taskType", taskType)
                .tag("result", result)
                .register(meterRegistry));
        meterRegistry.counter("finsight.workflow.task.total", "taskType", taskType, "result", result).increment();
    }

    private String stringPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing workflow payload key: " + key);
        }
        return String.valueOf(value);
    }
}
