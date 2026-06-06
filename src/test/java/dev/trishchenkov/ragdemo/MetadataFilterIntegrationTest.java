package dev.trishchenkov.ragdemo;

import dev.trishchenkov.ragdemo.service.IngestionService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies metadata-scoped retrieval and deletion against real pgvector: ingest with metadata,
 * retrieve filtered by it, then delete by the same filter. Skipped when Docker is unavailable.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MetadataFilterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void anthropicKey(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.anthropic.api-key", () -> "test-key");
    }

    @Autowired
    IngestionService ingestionService;

    @Autowired
    VectorStore vectorStore;

    @Test
    void retrievesByMetadataThenDeletesByFilter() {
        ingestionService.ingestText("Acme widget pricing and discount tiers.", Map.of("category", "pricing"));
        ingestionService.ingestText("Office holiday schedule for the year.", Map.of("category", "hr"));

        List<Document> pricing = search("widget price", "category == 'pricing'");
        assertThat(pricing).isNotEmpty();
        assertThat(pricing).allMatch(doc -> "pricing".equals(doc.getMetadata().get("category")));

        ingestionService.deleteByFilter("category == 'pricing'");

        assertThat(search("widget price", "category == 'pricing'")).isEmpty();
    }

    private List<Document> search(String query, String filter) {
        return vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(5).filterExpression(filter).build());
    }
}
