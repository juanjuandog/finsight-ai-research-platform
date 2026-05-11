package com.finsight.workflow;

public final class WorkflowTaskType {
    public static final String FINANCIAL_DATA_INGESTION = "FINANCIAL_DATA_INGESTION";
    public static final String FINANCIAL_METRIC_RECALCULATION = "FINANCIAL_METRIC_RECALCULATION";
    public static final String DOCUMENT_INDEX_BUILD = "DOCUMENT_INDEX_BUILD";
    public static final String COMPANY_INTELLIGENCE_BUILD = "COMPANY_INTELLIGENCE_BUILD";

    private WorkflowTaskType() {
    }
}
