package dev.trishchenkov.ragdemo.web;

import dev.trishchenkov.ragdemo.dto.Answer;
import dev.trishchenkov.ragdemo.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false) // this slice verifies validation/contract, not security
class ChatControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RagService ragService;

    @Test
    void rejectsBlankQuestionWith400() throws Exception {
        mvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void returnsAnswerForValidQuestion() throws Exception {
        when(ragService.answer(any(), any())).thenReturn(new Answer("Spring Boot simplifies setup.", List.of("doc-1")));

        mvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"What is Spring Boot?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Spring Boot simplifies setup."))
                .andExpect(jsonPath("$.sources[0]").value("doc-1"));
    }
}
