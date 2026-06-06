package dev.trishchenkov.ragdemo.web;

import dev.trishchenkov.ragdemo.dto.Answer;
import dev.trishchenkov.ragdemo.dto.Question;
import dev.trishchenkov.ragdemo.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
class ChatController {

    private final RagService ragService;

    ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/chat")
    Answer chat(@Valid @RequestBody Question question) {
        return ragService.answer(question.question(), question.filter());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> chatStream(@Valid @RequestBody Question question) {
        return ragService.answerStream(question.question(), question.filter());
    }
}
