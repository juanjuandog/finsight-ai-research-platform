package com.finsight.workflow;

import com.finsight.application.MetricApplicationService;
import com.finsight.application.DocumentIndexingService;
import com.finsight.application.CompanyIntelligenceService;
import com.finsight.domain.FinancialDataIngestionTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WorkflowOrchestrator {
    private final WorkflowTaskRepository taskRepository;
    private final FinancialDataIngestionTemplate dataSource;
    private final MetricApplicationService metricApplicationService;
    private final DocumentIndexingService documentIndexingService;
    private final CompanyIntelligenceService companyIntelligenceService;

    public WorkflowOrchestrator(
            WorkflowTaskRepository taskRepository,
            FinancialDataIngestionTemplate dataSource,
            MetricApplicationService metricApplicationService,
            DocumentIndexingService documentIndexingService,
            CompanyIntelligenceService companyIntelligenceService
    ) {
        this.taskRepository = taskRepository;
        this.dataSource = dataSource;
        this.metricApplicationService = metricApplicationService;
        this.documentIndexingService = documentIndexingService;
        this.companyIntelligenceService = companyIntelligenceService;
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
                WorkflowTask metricTask = WorkflowTask.created(
                        WorkflowTaskType.FINANCIAL_METRIC_RECALCULATION,
                        "metric:" + companySymbol + ":" + task.id(),
                        Map.of("companySymbol", companySymbol, "parentTaskId", task.id())
                );
                WorkflowTask indexTask = WorkflowTask.created(
                        WorkflowTaskType.DOCUMENT_INDEX_BUILD,
                        "index:" + companySymbol + ":" + task.id(),
                        Map.of("companySymbol", companySymbol, "parentTaskId", task.id())
                );
                WorkflowTask intelligenceTask = WorkflowTask.created(
                        WorkflowTaskType.COMPANY_INTELLIGENCE_BUILD,
                        "intelligence:" + companySymbol + ":" + task.id(),
                        Map.of("companySymbol", companySymbol, "parentTaskId", task.id())
                );
                taskRepository.save(metricTask);
                taskRepository.save(indexTask);
                taskRepository.save(intelligenceTask);
                execute(metricTask.id());
                execute(indexTask.id());
                execute(intelligenceTask.id());
            }
            case WorkflowTaskType.FINANCIAL_METRIC_RECALCULATION -> {
                WorkflowTask runningTask = task.running();
                try {
                    taskRepository.save(runningTask);
                    metricApplicationService.recalculate(stringPayload(task.payload(), "companySymbol"));
                    taskRepository.save(runningTask.succeeded());
                } catch (RuntimeException ex) {
                    taskRepository.save(runningTask.failed(ex.getMessage()));
                    throw ex;
                }
            }
            case WorkflowTaskType.DOCUMENT_INDEX_BUILD -> {
                WorkflowTask runningTask = task.running();
                try {
                    taskRepository.save(runningTask);
                    documentIndexingService.indexCompany(stringPayload(task.payload(), "companySymbol"));
                    taskRepository.save(runningTask.succeeded());
                } catch (RuntimeException ex) {
                    taskRepository.save(runningTask.failed(ex.getMessage()));
                    throw ex;
                }
            }
            case WorkflowTaskType.COMPANY_INTELLIGENCE_BUILD -> {
                WorkflowTask runningTask = task.running();
                try {
                    taskRepository.save(runningTask);
                    companyIntelligenceService.rebuild(stringPayload(task.payload(), "companySymbol"));
                    taskRepository.save(runningTask.succeeded());
                } catch (RuntimeException ex) {
                    taskRepository.save(runningTask.failed(ex.getMessage()));
                    throw ex;
                }
            }
            default -> throw new IllegalArgumentException("Unsupported workflow task type: " + task.taskType());
        }
    }

    private String stringPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing workflow payload key: " + key);
        }
        return String.valueOf(value);
    }
}
