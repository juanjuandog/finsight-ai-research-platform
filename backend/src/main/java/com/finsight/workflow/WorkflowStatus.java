package com.finsight.workflow;

public enum WorkflowStatus {
    CREATED,
    RUNNING,
    RETRYING,
    SUCCEEDED,
    FAILED,
    COMPENSATING,
    DEAD_LETTER
}
