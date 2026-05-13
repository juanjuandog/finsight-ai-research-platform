package com.finsight.ai;

import com.finsight.domain.model.EvidenceChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "finsight.ai-service.enabled", havingValue = "true")
public class RestAiServiceClient implements AiServiceClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final FallbackAiServiceClient fallback = new FallbackAiServiceClient();

    public RestAiServiceClient(WebClient.Builder builder, @Value("${finsight.ai-service-url}") String aiServiceUrl) {
        this.webClient = builder.baseUrl(aiServiceUrl).build();
    }

    @Override
    public List<EvidenceChunk> rerank(String question, List<EvidenceChunk> candidates) {
        try {
            RerankResponse response = webClient.post()
                    .uri("/rerank")
                    .bodyValue(new RerankRequest(question, candidates))
                    .retrieve()
                    .bodyToMono(RerankResponse.class)
                    .block(TIMEOUT);
            if (response != null && response.evidence() != null) {
                return response.evidence();
            }
        } catch (RuntimeException ignored) {
            // Keep the RAG path available when the sidecar AI service is unavailable.
        }
        return fallback.rerank(question, candidates);
    }

    @Override
    public String generateAnswer(String question, Map<String, Object> structuredQuery, List<EvidenceChunk> evidence) {
        try {
            GenerateAnswerResponse response = webClient.post()
                    .uri("/generate-answer")
                    .bodyValue(new GenerateAnswerRequest(question, structuredQuery, evidence))
                    .retrieve()
                    .bodyToMono(GenerateAnswerResponse.class)
                    .block(TIMEOUT);
            if (response != null && response.answer() != null && !response.answer().isBlank()) {
                return response.answer();
            }
        } catch (RuntimeException ignored) {
            // Keep answer generation deterministic for local demos and regression tests.
        }
        return fallback.generateAnswer(question, structuredQuery, evidence);
    }

    private record RerankRequest(String question, List<EvidenceChunk> candidates) {
    }

    private record RerankResponse(List<EvidenceChunk> evidence) {
    }

    private record GenerateAnswerRequest(
            String question,
            Map<String, Object> structuredQuery,
            List<EvidenceChunk> evidence
    ) {
    }

    private record GenerateAnswerResponse(String answer) {
    }
}
