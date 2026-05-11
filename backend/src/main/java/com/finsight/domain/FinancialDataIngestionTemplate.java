package com.finsight.domain;

import com.finsight.domain.model.Company;
import com.finsight.domain.model.FinancialDocument;
import com.finsight.domain.model.FinancialStatement;
import com.finsight.domain.repository.CompanyRepository;
import com.finsight.domain.repository.DocumentRepository;
import com.finsight.domain.repository.FinancialStatementRepository;
import com.finsight.workflow.WorkflowTask;
import com.finsight.workflow.WorkflowTaskRepository;
import com.finsight.workflow.WorkflowTaskType;

import java.util.List;
import java.util.Map;

public abstract class FinancialDataIngestionTemplate implements FinancialDataSource {
    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;
    private final FinancialStatementRepository statementRepository;
    private final WorkflowTaskRepository taskRepository;

    protected FinancialDataIngestionTemplate(
            CompanyRepository companyRepository,
            DocumentRepository documentRepository,
            FinancialStatementRepository statementRepository,
            WorkflowTaskRepository taskRepository
    ) {
        this.companyRepository = companyRepository;
        this.documentRepository = documentRepository;
        this.statementRepository = statementRepository;
        this.taskRepository = taskRepository;
    }

    public IngestionResult ingestCompany(String companySymbol) {
        String idempotencyKey = sourceName() + ":" + companySymbol;
        if (taskRepository.existsByIdempotencyKey(idempotencyKey)) {
            return new IngestionResult(sourceName(), companySymbol, 0, 0, true);
        }

        WorkflowTask task = taskRepository.save(WorkflowTask.created(
                WorkflowTaskType.FINANCIAL_DATA_INGESTION,
                idempotencyKey,
                Map.of("source", sourceName(), "companySymbol", companySymbol)
        ));

        return executeIngestionTask(task);
    }

    public WorkflowTask createIngestionTask(String companySymbol) {
        String idempotencyKey = sourceName() + ":" + companySymbol;
        return taskRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> taskRepository.save(WorkflowTask.created(
                        WorkflowTaskType.FINANCIAL_DATA_INGESTION,
                        idempotencyKey,
                        Map.of("source", sourceName(), "companySymbol", companySymbol)
                )));
    }

    public IngestionResult executeIngestionTask(WorkflowTask task) {
        String companySymbol = String.valueOf(task.payload().get("companySymbol"));
        WorkflowTask runningTask = task.running();
        try {
            taskRepository.save(runningTask);
            List<Company> companies = fetchCompanies();
            companies.forEach(companyRepository::save);
            List<FinancialDocument> documents = fetchDocuments(companySymbol);
            documents.stream().filter(this::isValidDocument).forEach(documentRepository::save);
            List<FinancialStatement> statements = fetchStatements(companySymbol);
            statements.forEach(statementRepository::save);
            taskRepository.save(runningTask.succeeded());
            return new IngestionResult(sourceName(), companySymbol, documents.size(), statements.size(), false);
        } catch (RuntimeException ex) {
            taskRepository.save(runningTask.failed(ex.getMessage()));
            throw ex;
        }
    }

    protected boolean isValidDocument(FinancialDocument document) {
        return document.id() != null
                && document.companySymbol() != null
                && document.content() != null
                && !document.content().isBlank();
    }

    public record IngestionResult(
            String source,
            String companySymbol,
            int documentCount,
            int statementCount,
            boolean duplicated
    ) {
    }
}
