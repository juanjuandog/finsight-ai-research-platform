package com.finsight.workflow;

import com.finsight.application.MetricApplicationService;
import com.finsight.application.DocumentIndexingService;
import com.finsight.application.CompanyIntelligenceService;
import com.finsight.application.StockAiAnalysisService;
import com.finsight.domain.FinancialDataIngestionTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkflowOrchestrator {
    private final WorkflowTaskRepository taskRepository;
    private final FinancialDataIngestionTemplate dataSource;
    private final MetricApplicationService metricApplicationService;
    private final DocumentIndexingService documentIndexingService;
    private final CompanyIntelligenceService companyIntelligenceService;
    private final StockAiAnalysisService stockAiAnalysisService;
    private final WorkflowLeaseService leaseService;
    private final MeterRegistry meterRegistry;
    private final Duration leaseTtl = Duration.ofMinutes(5);

    public WorkflowOrchestrator(
            WorkflowTaskRepository taskRepository,
            FinancialDataIngestionTemplate dataSource,
            MetricApplicationService metricApplicationService,
            DocumentIndexingService documentIndexingService,
            CompanyIntelligenceService companyIntelligenceService,
            StockAiAnalysisService stockAiAnalysisService,
            WorkflowLeaseService leaseService,
            MeterRegistry meterRegistry
    ) {
        this.taskRepository = taskRepository;
        this.dataSource = dataSource;
        this.metricApplicationService = metricApplicationService;
        this.documentIndexingService = documentIndexingService;
        this.companyIntelligenceService = companyIntelligenceService;
        this.stockAiAnalysisService = stockAiAnalysisService;
        this.leaseService = leaseService;
        this.meterRegistry = meterRegistry;
    }

    public void execute(String taskId) {
        WorkflowTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow task not found: " + taskId));
        if (task.status() == WorkflowStatus.SUCCEEDED) {
            return;
        }
        Optional<WorkflowLease> lease = leaseService.tryAcquire(task.idempotencyKey(), leaseTtl);
        if (lease.isEmpty()) {
            taskRepository.save(task.waitingForLease());
            recordWorkflowMetric(task.taskType(), "lease_wait", Timer.start(meterRegistry));
            return;
        }
        try {
            executeWithLease(task, lease.get());
        } finally {
            leaseService.release(lease.get());
        }
    }

    private void executeWithLease(WorkflowTask task, WorkflowLease lease) {
        switch (task.taskType()) {
            case WorkflowTaskType.FINANCIAL_DATA_INGESTION -> executeIngestion(task, lease);
            case WorkflowTaskType.FINANCIAL_METRIC_RECALCULATION -> executeTask(task, lease, AgentWorkflowStage.METRIC_CALCULATING,
                    () -> metricApplicationService.recalculate(stringPayload(task.payload(), "companySymbol")));
            case WorkflowTaskType.DOCUMENT_INDEX_BUILD -> executeTask(task, lease, AgentWorkflowStage.DOCUMENT_INDEXING,
                    () -> documentIndexingService.indexCompany(stringPayload(task.payload(), "companySymbol")));
            case WorkflowTaskType.COMPANY_INTELLIGENCE_BUILD -> executeTask(task, lease, AgentWorkflowStage.INTELLIGENCE_BUILDING,
                    () -> companyIntelligenceService.rebuild(stringPayload(task.payload(), "companySymbol")));
            case WorkflowTaskType.STOCK_AI_ANALYSIS -> executeTask(task, lease, AgentWorkflowStage.AI_ANALYZING,
                    () -> stockAiAnalysisService.analyze(stringPayload(task.payload(), "companySymbol")));
            default -> throw new IllegalArgumentException("Unsupported workflow task type: " + task.taskType());
        }
    }

    private void executeIngestion(WorkflowTask task, WorkflowLease lease) {
        WorkflowTask runningTask = task.running(AgentWorkflowStage.INGESTING_DATA, lease);
        taskRepository.save(runningTask);
        dataSource.executeIngestionTask(runningTask);
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

    private WorkflowTask createOrReuseTask(String taskType, String idempotencyKey, Map<String, Object> payload) {
        return taskRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> taskRepository.createIfAbsent(WorkflowTask.created(taskType, idempotencyKey, payload)));
    }

    private void executeTask(WorkflowTask task, WorkflowLease lease, AgentWorkflowStage stage, Runnable runnable) {
        WorkflowTask runningTask = task.running(stage, lease);
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
