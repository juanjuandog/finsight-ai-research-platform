package com.finsight.application;

import com.finsight.domain.model.AnswerResponse;
import com.finsight.domain.model.AskQuestionCommand;
import com.finsight.domain.model.EvidenceChunk;
import com.finsight.evaluation.EvaluationCase;
import com.finsight.evaluation.EvaluationCaseCatalog;
import com.finsight.evaluation.EvaluationResult;
import com.finsight.evaluation.EvaluationRun;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RagEvaluationService {
    private final EvaluationCaseCatalog catalog;
    private final AnalysisApplicationService analysisApplicationService;

    public RagEvaluationService(EvaluationCaseCatalog catalog, AnalysisApplicationService analysisApplicationService) {
        this.catalog = catalog;
        this.analysisApplicationService = analysisApplicationService;
    }

    public List<EvaluationCase> cases() {
        return catalog.cases();
    }

    public EvaluationRun run() {
        Instant startedAt = Instant.now();
        List<EvaluationResult> results = catalog.cases().stream()
                .map(this::evaluate)
                .toList();
        int passed = (int) results.stream().filter(EvaluationResult::passed).count();
        double averageScore = results.stream()
                .mapToDouble(EvaluationResult::score)
                .average()
                .orElse(0);
        return new EvaluationRun(
                UUID.randomUUID().toString(),
                results.size(),
                passed,
                averageScore,
                results,
                startedAt,
                Instant.now()
        );
    }

    private EvaluationResult evaluate(EvaluationCase testCase) {
        AnswerResponse response = analysisApplicationService.ask(new AskQuestionCommand(
                testCase.question(),
                testCase.companySymbol(),
                testCase.timeRange()
        ));
        List<String> failures = new ArrayList<>();
        double score = 0;

        double evidenceScore = evidenceCoverage(testCase, response);
        score += evidenceScore * 0.45;
        if (evidenceScore < 0.6) {
            failures.add("Evidence coverage below threshold");
        }

        double answerScore = answerCoverage(testCase, response);
        score += answerScore * 0.35;
        if (answerScore < 0.6) {
            failures.add("Answer keyword coverage below threshold");
        }

        boolean hasCitation = !response.evidence().isEmpty()
                && (response.answer().contains("引用来源") || response.answer().contains("以上结论来自"));
        score += hasCitation ? 0.1 : 0;
        if (!hasCitation) {
            failures.add("Answer missing citation wording or evidence");
        }

        boolean latencyOk = response.trace().latencyMillis() <= testCase.maxLatencyMillis();
        score += latencyOk ? 0.1 : 0;
        if (!latencyOk) {
            failures.add("Latency exceeded " + testCase.maxLatencyMillis() + "ms");
        }

        double normalized = Math.min(1.0, score);
        return new EvaluationResult(
                testCase.id(),
                failures.isEmpty(),
                normalized,
                failures,
                Map.of(
                        "evidenceCoverage", evidenceScore,
                        "answerCoverage", answerScore,
                        "hasCitation", hasCitation,
                        "latencyMillis", response.trace().latencyMillis(),
                        "evidenceCount", response.evidence().size()
                ),
                response,
                Instant.now()
        );
    }

    private double evidenceCoverage(EvaluationCase testCase, AnswerResponse response) {
        String evidenceText = response.evidence().stream()
                .map(this::evidenceText)
                .reduce("", (left, right) -> left + " " + right);
        return coverage(testCase.requiredEvidenceKeywords(), evidenceText);
    }

    private String evidenceText(EvidenceChunk chunk) {
        return chunk.title() + " " + chunk.section() + " " + chunk.text();
    }

    private double answerCoverage(EvaluationCase testCase, AnswerResponse response) {
        return coverage(testCase.requiredAnswerKeywords(), response.answer());
    }

    private double coverage(List<String> required, String text) {
        if (required.isEmpty()) {
            return 1;
        }
        long matched = required.stream()
                .filter(keyword -> text != null && text.contains(keyword))
                .count();
        return matched / (double) required.size();
    }
}
