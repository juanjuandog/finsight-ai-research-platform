package com.finsight.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WorkflowTask(
        String id,
        String taskType,
        String idempotencyKey,
        WorkflowStatus status,
        int attempts,
        Instant createdAt,
        Map<String, Object> payload,
        String errorMessage
) {
    public static WorkflowTask created(String taskType, String idempotencyKey, Map<String, Object> payload) {
        return new WorkflowTask(
                UUID.randomUUID().toString(),
                taskType,
                idempotencyKey,
                WorkflowStatus.CREATED,
                0,
                Instant.now(),
                payload,
                null
        );
    }

    public WorkflowTask running() {
        return new WorkflowTask(id, taskType, idempotencyKey, WorkflowStatus.RUNNING, attempts + 1, createdAt, payload, null);
    }

    public WorkflowTask succeeded() {
        return new WorkflowTask(id, taskType, idempotencyKey, WorkflowStatus.SUCCEEDED, attempts, createdAt, payload, null);
    }

    public WorkflowTask failed(String message) {
        WorkflowStatus nextStatus = attempts >= 3 ? WorkflowStatus.DEAD_LETTER : WorkflowStatus.FAILED;
        return new WorkflowTask(id, taskType, idempotencyKey, nextStatus, attempts, createdAt, payload, message);
    }
}

