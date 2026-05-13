package com.finsight.rag;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingServiceTest {
    @Test
    void deterministicEmbeddingUsesConfiguredDimension() {
        EmbeddingService service = new EmbeddingService(
                WebClient.builder(),
                "http://localhost:8001",
                false,
                384
        );

        List<Double> first = service.embed("现金流质量和利润含金量");
        List<Double> second = service.embed("现金流质量和利润含金量");

        assertThat(first).hasSize(384);
        assertThat(first).isEqualTo(second);
    }
}
