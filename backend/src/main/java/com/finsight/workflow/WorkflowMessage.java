package com.finsight.workflow;

import java.time.Instant;
import java.util.Map;

public record WorkflowMessage(
        String taskId,
        String taskType,
        String idempotencyKey,
        Map<String, Object> payload,
        Instant dispatchedAt
) {
    public static WorkflowMessage from(WorkflowTask task) {
        return new WorkflowMessage(
                task.id(),
                task.taskType(),
                task.idempotencyKey(),
                task.payload(),
                Instant.now()
        );
    }
}

