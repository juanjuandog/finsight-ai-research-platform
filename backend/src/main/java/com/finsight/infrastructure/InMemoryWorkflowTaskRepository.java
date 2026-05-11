package com.finsight.infrastructure;

import com.finsight.workflow.WorkflowTask;
import com.finsight.workflow.WorkflowTaskRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    public Optional<WorkflowTask> findById(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public Optional<WorkflowTask> findByIdempotencyKey(String idempotencyKey) {
        return tasks.values().stream()
                .filter(task -> task.idempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    @Override
    public List<WorkflowTask> findAll() {
        return new ArrayList<>(tasks.values());
    }
}
