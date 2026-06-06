package dev.trishchenkov.ragdemo.service;

import dev.trishchenkov.ragdemo.config.RagProperties;
import dev.trishchenkov.ragdemo.dto.Answer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagServiceTest {

    @Test
    void returnsFallbackAndSkipsLlmWhenNothingRetrieved() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);

        RagService service = new RagService(vectorStore, builder, new RagProperties(4));

        Answer answer = service.answer("anything", null);

        assertThat(answer.sources()).isEmpty();
        assertThat(answer.answer()).containsIgnoringCase("don't have");
        verifyNoInteractions(chatClient);
    }
}
