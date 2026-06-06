package dev.trishchenkov.ragdemo.service;

import dev.trishchenkov.ragdemo.config.RagProperties;
import dev.trishchenkov.ragdemo.dto.Answer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final String PROMPT = """
            Answer the question using only the context below.
            If the context does not contain the answer, say you don't know — do not invent facts.

            Context:
            {context}

            Question: {question}
            """;

    private static final String NO_CONTEXT_ANSWER =
            "I don't have any indexed documents relevant to that question.";

    private static final String FALLBACK_ANSWER =
            "The assistant is temporarily unavailable. Please try again shortly.";

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final int topK;

    RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.topK = properties.topK();
    }

    @CircuitBreaker(name = "llm", fallbackMethod = "answerFallback")
    public Answer answer(String question, String filter) {
        List<Document> retrieved = retrieve(question, filter);
        if (retrieved.isEmpty()) {
            return new Answer(NO_CONTEXT_ANSWER, List.of());
        }
        String answer = chatClient.prompt().user(grounded(retrieved, question)).call().content();
        return new Answer(answer, texts(retrieved));
    }

    @SuppressWarnings("unused") // invoked by Resilience4j when the "llm" circuit is open or the call fails
    Answer answerFallback(String question, String filter, Throwable t) {
        log.warn("LLM call failed for question [{}]; returning fallback", question, t);
        return new Answer(FALLBACK_ANSWER, List.of());
    }

    public Flux<String> answerStream(String question, String filter) {
        List<Document> retrieved = retrieve(question, filter);
        if (retrieved.isEmpty()) {
            return Flux.just(NO_CONTEXT_ANSWER);
        }
        return chatClient.prompt().user(grounded(retrieved, question)).stream().content()
                .onErrorResume(e -> {
                    log.warn("Streaming LLM call failed for question [{}]; returning fallback", question, e);
                    return Flux.just(FALLBACK_ANSWER);
                });
    }

    private List<Document> retrieve(String question, String filter) {
        SearchRequest.Builder request = SearchRequest.builder().query(question).topK(topK);
        if (filter != null && !filter.isBlank()) {
            request.filterExpression(filter);
        }
        return vectorStore.similaritySearch(request.build());
    }

    private Consumer<ChatClient.PromptUserSpec> grounded(List<Document> documents, String question) {
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
        return spec -> spec.text(PROMPT).param("context", context).param("question", question);
    }

    private List<String> texts(List<Document> documents) {
        return documents.stream().map(Document::getText).toList();
    }
}
