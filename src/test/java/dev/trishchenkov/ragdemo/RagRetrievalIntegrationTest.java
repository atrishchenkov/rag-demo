package dev.trishchenkov.ragdemo;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the embedding + storage + retrieval path against a real pgvector instance.
 * Skipped automatically when Docker is unavailable; runs in CI and locally where Docker is present.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RagRetrievalIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void anthropicKey(DynamicPropertyRegistry registry) {
        // Chat model is never called in this test; a placeholder key satisfies auto-configuration.
        registry.add("spring.ai.anthropic.api-key", () -> "test-key");
    }

    @Autowired
    VectorStore vectorStore;

    @Test
    void retrievesMostRelevantIngestedDocument() {
        vectorStore.add(List.of(
                new Document("Spring Boot is a Java framework for building stand-alone applications."),
                new Document("Kafka is a distributed event-streaming platform."),
                new Document("PostgreSQL is an open-source relational database.")));

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query("What is Spring Boot?").topK(1).build());

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getText()).contains("Spring Boot");
    }
}
