package com.finsight.application;

import com.finsight.domain.FinancialDataIngestionTemplate;
import com.finsight.workflow.WorkflowTask;
import com.finsight.workflow.WorkflowTaskPublisher;
import com.finsight.workflow.WorkflowTaskRepository;
import org.springframework.stereotype.Service;

@Service
public class IngestionApplicationService {
    private final FinancialDataIngestionTemplate dataSource;
    private final WorkflowTaskPublisher workflowTaskPublisher;
    private final WorkflowTaskRepository workflowTaskRepository;

    public IngestionApplicationService(
            FinancialDataIngestionTemplate dataSource,
            WorkflowTaskPublisher workflowTaskPublisher,
            WorkflowTaskRepository workflowTaskRepository
    ) {
        this.dataSource = dataSource;
        this.workflowTaskPublisher = workflowTaskPublisher;
        this.workflowTaskRepository = workflowTaskRepository;
    }

    public FinancialDataIngestionTemplate.IngestionResult ingestDemoCompany() {
        return dataSource.ingestCompany("600519");
    }

    public WorkflowSubmission submitDemoCompanyIngestion() {
        WorkflowTask task = dataSource.createIngestionTask("600519");
        workflowTaskPublisher.publish(task);
        WorkflowTask current = workflowTaskRepository.findById(task.id()).orElse(task);
        return new WorkflowSubmission(current.id(), current.taskType(), current.idempotencyKey(), current.status().name());
    }

    public record WorkflowSubmission(
            String taskId,
            String taskType,
            String idempotencyKey,
            String status
    ) {
    }
}
