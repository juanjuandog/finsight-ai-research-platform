package com.finsight.evaluation;

import java.time.Instant;
import java.util.List;

public record EvaluationRun(
        String id,
        int totalCases,
        int passedCases,
        double averageScore,
        List<EvaluationResult> results,
        Instant startedAt,
        Instant finishedAt
) {
}

