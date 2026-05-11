package com.finsight.infrastructure;

import com.finsight.workflow.WorkflowTask;
import com.finsight.workflow.WorkflowTaskRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryWorkflowTaskRepository implements WorkflowTaskRepository {
    private final ConcurrentHashMap<String, WorkflowTask> tasks = new ConcurrentHashMap<>();

    @Override
    public WorkflowTask save(WorkflowTask task) {
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return tasks.values().stream().anyMatch(task -> task.idempotencyKey().equals(idempotencyKey));
    }

    @Override
    public List<WorkflowTask> findAll() {
        return new ArrayList<>(tasks.values());
    }
}
