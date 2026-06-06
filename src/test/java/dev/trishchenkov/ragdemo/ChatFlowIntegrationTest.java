package dev.trishchenkov.ragdemo;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Full RAG flow over real pgvector with the LLM stubbed at the {@link ChatModel} bean boundary.
 * Verifies ingest → retrieve → grounded prompt → generation → response (with sources) over the real
 * HTTP endpoints, without a real API key or cost. Deterministic; skipped when Docker is unavailable.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@Import({ChatFlowIntegrationTest.StubLlmConfig.class, TestSecurityConfig.class})
class ChatFlowIntegrationTest {

    private static final String CANNED_ANSWER =
            "Spring Boot is a Java framework for building stand-alone applications.";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void anthropicKey(DynamicPropertyRegistry registry) {
        // The real provider bean is created but never called (LLM is stubbed); placeholder key
        // satisfies auto-configuration.
        registry.add("spring.ai.anthropic.api-key", () -> "test-key");
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void ingestThenChatReturnsGroundedAnswerWithSources() {
        ingestSampleDocument();

        var chat = rest.postForEntity("/chat",
                Map.of("question", "What is Spring Boot?"), ChatResponseBody.class);

        assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chat.getBody()).isNotNull();
        assertThat(chat.getBody().answer()).contains("Spring Boot");
        assertThat(chat.getBody().sources()).isNotEmpty();
    }

    @Test
    void chatStreamReturnsGroundedAnswer() {
        ingestSampleDocument();

        var stream = rest.postForEntity("/chat/stream",
                Map.of("question", "What is Spring Boot?"), String.class);

        assertThat(stream.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stream.getBody()).contains("Spring Boot");
    }

    private void ingestSampleDocument() {
        var ingest = rest.postForEntity("/documents",
                Map.of("text", "Spring Boot is a Java framework for building stand-alone, production-grade applications."),
                IngestResponse.class);
        assertThat(ingest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ingest.getBody()).isNotNull();
        assertThat(ingest.getBody().chunksIndexed()).isGreaterThanOrEqualTo(1);
    }

    @TestConfiguration
    static class StubLlmConfig {
        @Bean
        @Primary
        ChatModel stubChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    return response();
                }

                @Override
                public Flux<ChatResponse> stream(Prompt prompt) {
                    return Flux.just(response());
                }

                private ChatResponse response() {
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(CANNED_ANSWER))));
                }
            };
        }
    }

    record IngestResponse(int chunksIndexed) {}

    record ChatResponseBody(String answer, List<String> sources) {}
}
