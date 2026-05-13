package com.finsight.workflow;

public enum AgentWorkflowStage {
    CREATED,
    LEASE_ACQUIRED,
    INGESTING_DATA,
    METRIC_CALCULATING,
    DOCUMENT_INDEXING,
    INTELLIGENCE_BUILDING,
    AI_ANALYZING,
    SUCCEEDED,
    FAILED,
    RECOVERING,
    LEASE_WAIT
}
