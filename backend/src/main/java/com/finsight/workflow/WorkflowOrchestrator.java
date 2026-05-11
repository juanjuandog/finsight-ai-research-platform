package com.finsight.workflow;

import com.finsight.application.MetricApplicationService;
import com.finsight.domain.FinancialDataIngestionTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WorkflowOrchestrator {
    private final WorkflowTaskRepository taskRepository;
    private final FinancialDataIngestionTemplate dataSource;
    private final MetricApplicationService metricApplicationService;

    public WorkflowOrchestrator(
            WorkflowTaskRepository taskRepository,
            FinancialDataIngestionTemplate dataSource,
            MetricApplicationService metricApplicationService
    ) {
        this.taskRepository = taskRepository;
        this.dataSource = dataSource;
        this.metricApplicationService = metricApplicationService;
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
                taskRepository.save(metricTask);
                execute(metricTask.id());
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
            case WorkflowTaskType.DOCUMENT_INDEX_BUILD -> markSucceeded(task);
            default -> throw new IllegalArgumentException("Unsupported workflow task type: " + task.taskType());
        }
    }

    private void markSucceeded(WorkflowTask task) {
        WorkflowTask runningTask = task.running();
        taskRepository.save(runningTask);
        taskRepository.save(runningTask.succeeded());
    }

    private String stringPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing workflow payload key: " + key);
        }
        return String.valueOf(value);
    }
}

