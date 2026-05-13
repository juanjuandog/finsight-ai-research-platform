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
        score += evidenceScore * 0.25;
        if (evidenceScore < 0.6) {
            failures.add("Evidence coverage below threshold");
        }

        double answerScore = answerCoverage(testCase, response);
        score += answerScore * 0.30;
        if (answerScore < 0.6) {
            failures.add("Answer keyword coverage below threshold");
        }

        double hallucinationRisk = hallucinationRisk(response);
        score += (1.0 - hallucinationRisk) * 0.15;
        if (hallucinationRisk > 0.35) {
            failures.add("Hallucination risk above threshold");
        }

        double consistencyScore = conclusionConsistency(response);
        score += consistencyScore * 0.1;
        if (consistencyScore < 0.6) {
            failures.add("Conclusion consistency below threshold");
        }

        double confidenceCalibration = confidenceCalibration(evidenceScore, answerScore, hallucinationRisk);
        score += confidenceCalibration * 0.1;

        boolean hasCitation = !response.evidence().isEmpty()
                && (response.answer().contains("引用来源") || response.answer().contains("以上结论来自"));
        score += hasCitation ? 0.05 : 0;
        if (!hasCitation) {
            failures.add("Answer missing citation wording or evidence");
        }

        boolean latencyOk = response.trace().latencyMillis() <= testCase.maxLatencyMillis();
        score += latencyOk ? 0.05 : 0;
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
                        "ragHitRate", ragHitRate(testCase, response),
                        "hallucinationRisk", hallucinationRisk,
                        "conclusionConsistency", consistencyScore,
                        "confidenceCalibration", confidenceCalibration,
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

    private double ragHitRate(EvaluationCase testCase, AnswerResponse response) {
        if (response.evidence().isEmpty()) {
            return 0;
        }
        long hits = response.evidence().stream()
                .filter(chunk -> coverage(testCase.requiredEvidenceKeywords(), evidenceText(chunk)) > 0)
                .count();
        return hits / (double) response.evidence().size();
    }

    private double hallucinationRisk(AnswerResponse response) {
        if (response.answer() == null || response.answer().isBlank()) {
            return 1;
        }
        String evidenceText = response.evidence().stream()
                .map(this::evidenceText)
                .reduce("", (left, right) -> left + " " + right);
        List<String> riskyClaims = List.of("必然", "保证", "确定", "无风险", "翻倍", "稳赚", "唯一原因");
        long riskyCount = riskyClaims.stream().filter(response.answer()::contains).count();
        double unsupportedPenalty = response.evidence().isEmpty() ? 0.6 : 0;
        double riskyWordPenalty = Math.min(0.4, riskyCount * 0.12);
        double evidenceOverlap = tokenOverlap(response.answer(), evidenceText);
        return Math.min(1.0, unsupportedPenalty + riskyWordPenalty + Math.max(0, 0.35 - evidenceOverlap));
    }

    private double conclusionConsistency(AnswerResponse response) {
        if (response.answer() == null || response.answer().isBlank()) {
            return 0;
        }
        boolean hasRisk = response.answer().contains("风险") || response.answer().contains("压力") || response.answer().contains("下滑");
        boolean hasPositive = response.answer().contains("改善") || response.answer().contains("增长") || response.answer().contains("优势");
        boolean hasHedge = response.answer().contains("同时") || response.answer().contains("但") || response.answer().contains("需要关注");
        if (hasRisk && hasPositive) {
            return hasHedge ? 1 : 0.5;
        }
        return 0.8;
    }

    private double confidenceCalibration(double evidenceScore, double answerScore, double hallucinationRisk) {
        double groundedScore = evidenceScore * 0.5 + answerScore * 0.35 + (1 - hallucinationRisk) * 0.15;
        if (groundedScore >= 0.8) {
            return 1;
        }
        if (groundedScore >= 0.6) {
            return 0.75;
        }
        if (groundedScore >= 0.4) {
            return 0.45;
        }
        return 0.2;
    }

    private double tokenOverlap(String answer, String evidenceText) {
        List<String> answerTokens = meaningfulTokens(answer);
        if (answerTokens.isEmpty() || evidenceText == null || evidenceText.isBlank()) {
            return 0;
        }
        long overlap = answerTokens.stream()
                .filter(token -> evidenceText.contains(token))
                .count();
        return overlap / (double) answerTokens.size();
    }

    private List<String> meaningfulTokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : text.split("[，。；、\\s:：,.!?！？()（）]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens.stream().limit(64).toList();
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
