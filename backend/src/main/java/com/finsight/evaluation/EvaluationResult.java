package com.finsight.evaluation;

import com.finsight.domain.model.AnswerResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EvaluationResult(
        String caseId,
        boolean passed,
        double score,
        List<String> failures,
        Map<String, Object> metrics,
        AnswerResponse answer,
        Instant evaluatedAt
) {
}

