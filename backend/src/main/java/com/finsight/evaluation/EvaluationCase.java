package com.finsight.evaluation;

import java.util.List;

public record EvaluationCase(
        String id,
        String companySymbol,
        String question,
        String timeRange,
        List<String> requiredEvidenceKeywords,
        List<String> requiredAnswerKeywords,
        int maxLatencyMillis
) {
}

