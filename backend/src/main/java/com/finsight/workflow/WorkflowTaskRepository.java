package com.finsight.workflow;

import java.util.List;

public interface WorkflowTaskRepository {
    WorkflowTask save(WorkflowTask task);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<WorkflowTask> findAll();
}

