package com.finsight.domain.model;

import java.time.Instant;
import java.util.List;

public record StockAnalysisReport(
        String id,
        String companySymbol,
        String rating,
        String summary,
        List<String> positivePoints,
        List<String> riskPoints,
        int confidence,
        List<String> citations,
        String model,
        String source,
        boolean aiGenerated,
        String contextHash,
        String dataSnapshotHash,
        int reportVersion,
        Instant generatedAt
) {
}
