package com.finsight.rag;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class EmbeddingService {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final int dimension;
    private final boolean remoteEnabled;
    private final WebClient webClient;

    public EmbeddingService(
            WebClient.Builder builder,
            @Value("${finsight.ai-service-url}") String aiServiceUrl,
            @Value("${finsight.ai-service.enabled:false}") boolean remoteEnabled,
            @Value("${finsight.embedding.dimension:384}") int dimension
    ) {
        this.webClient = builder.baseUrl(aiServiceUrl).build();
        this.remoteEnabled = remoteEnabled;
        this.dimension = dimension;
    }

    public List<Double> embed(String text) {
        String safeText = text == null ? "" : text;
        if (remoteEnabled) {
            try {
                EmbedResponse response = webClient.post()
                        .uri("/embed")
                        .bodyValue(new EmbedRequest(List.of(safeText), dimension))
                        .retrieve()
                        .bodyToMono(EmbedResponse.class)
                        .block(TIMEOUT);
                if (response != null && response.embeddings() != null && !response.embeddings().isEmpty()) {
                    List<Double> remoteVector = response.embeddings().get(0);
                    if (remoteVector.size() == dimension) {
                        return remoteVector;
                    }
                }
            } catch (RuntimeException ignored) {
                // Local deterministic vectors keep indexing/search working during AI sidecar outages.
            }
        }
        return deterministicEmbedding(safeText);
    }

    private List<Double> deterministicEmbedding(String text) {
        List<Double> vector = new ArrayList<>(dimension);
        byte[] seed = digest(text);
        for (int i = 0; i < dimension; i++) {
            byte[] digest = digest(text + ":" + i + ":" + Byte.toUnsignedInt(seed[i % seed.length]));
            int unsigned = Byte.toUnsignedInt(digest[0]);
            vector.add((unsigned / 127.5) - 1.0);
        }
        return vector;
    }

    public String hash(String text) {
        byte[] digest = digest(text == null ? "" : text);
        StringBuilder builder = new StringBuilder();
        for (byte b : digest) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private byte[] digest(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create deterministic embedding", ex);
        }
    }

    private record EmbedRequest(List<String> texts, int dimension) {
    }

    private record EmbedResponse(List<List<Double>> embeddings, String model, int dimension) {
    }
}
