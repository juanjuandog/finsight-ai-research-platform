package com.finsight;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles({"postgres", "rabbitmq", "prod"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "finsight.ai-service.enabled=false"
})
class FinSightInfrastructureSmokeTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("finsight")
            .withUsername("finsight")
            .withPassword("finsight");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management")
            .withUser("finsight", "finsight");

    @Autowired
    TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "finsight");
        registry.add("spring.rabbitmq.password", () -> "finsight");
    }

    @Test
    void applicationStartsWithPostgresPgvectorAndRabbitProfiles() {
        ResponseEntity<Map> health = restTemplate.getForEntity("/actuator/health", Map.class);
        ResponseEntity<Map> summary = restTemplate.getForEntity("/api/workflows/summary", Map.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody()).containsKeys("total", "counts", "failedOrDeadLetter");
    }
}
