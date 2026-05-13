package com.finsight.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WorkflowTask(
        String id,
        String taskType,
        String idempotencyKey,
        WorkflowStatus status,
        AgentWorkflowStage stage,
        int attempts,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> payload,
        String errorMessage,
        String leaseOwner,
        Long fencingToken
) {
    public static WorkflowTask created(String taskType, String idempotencyKey, Map<String, Object> payload) {
        Instant now = Instant.now();
        return new WorkflowTask(
                UUID.randomUUID().toString(),
                taskType,
                idempotencyKey,
                WorkflowStatus.CREATED,
                AgentWorkflowStage.CREATED,
                0,
                now,
                now,
                payload,
                null,
                null,
                null
        );
    }

    public WorkflowTask running(AgentWorkflowStage stage, WorkflowLease lease) {
        return new WorkflowTask(
                id,
                taskType,
                idempotencyKey,
                WorkflowStatus.RUNNING,
                stage,
                attempts + 1,
                createdAt,
                Instant.now(),
                payload,
                null,
                lease.owner(),
                lease.fencingToken()
        );
    }

    public WorkflowTask running() {
        return running(AgentWorkflowStage.LEASE_ACQUIRED, new WorkflowLease(idempotencyKey, "local", 0, Instant.now()));
    }

    public WorkflowTask atStage(AgentWorkflowStage stage) {
        return new WorkflowTask(
                id,
                taskType,
                idempotencyKey,
                status,
                stage,
                attempts,
                createdAt,
                Instant.now(),
                payload,
                errorMessage,
                leaseOwner,
                fencingToken
        );
    }

    public WorkflowTask succeeded() {
        return terminal(WorkflowStatus.SUCCEEDED, AgentWorkflowStage.SUCCEEDED, null);
    }

    public WorkflowTask failed(String message) {
        WorkflowStatus nextStatus = attempts >= 3 ? WorkflowStatus.DEAD_LETTER : WorkflowStatus.FAILED;
        return terminal(nextStatus, AgentWorkflowStage.FAILED, message);
    }

    public WorkflowTask retrying() {
        return new WorkflowTask(
                id,
                taskType,
                idempotencyKey,
                WorkflowStatus.RETRYING,
                AgentWorkflowStage.RECOVERING,
                attempts,
                createdAt,
                Instant.now(),
                payload,
                null,
                null,
                null
        );
    }

    public WorkflowTask waitingForLease() {
        return new WorkflowTask(
                id,
                taskType,
                idempotencyKey,
                status,
                AgentWorkflowStage.LEASE_WAIT,
                attempts,
                createdAt,
                Instant.now(),
                payload,
                errorMessage,
                leaseOwner,
                fencingToken
        );
    }

    public WorkflowTask recoveredAfterTimeout(String message) {
        WorkflowStatus nextStatus = attempts >= 3 ? WorkflowStatus.DEAD_LETTER : WorkflowStatus.FAILED;
        return new WorkflowTask(
                id,
                taskType,
                idempotencyKey,
                nextStatus,
                AgentWorkflowStage.RECOVERING,
                attempts,
                createdAt,
                Instant.now(),
                payload,
                message,
                null,
                null
        );
    }

    public boolean retryable() {
        return status == WorkflowStatus.FAILED || status == WorkflowStatus.DEAD_LETTER;
    }

    private WorkflowTask terminal(WorkflowStatus status, AgentWorkflowStage stage, String message) {
        return new WorkflowTask(
                id,
                taskType,
                idempotencyKey,
                status,
                stage,
                attempts,
                createdAt,
                Instant.now(),
                payload,
                message,
                null,
                null
        );
    }
}
