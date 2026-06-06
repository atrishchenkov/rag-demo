package dev.trishchenkov.ragdemo.security;

import dev.trishchenkov.ragdemo.service.IngestionService;
import dev.trishchenkov.ragdemo.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the OAuth2 resource-server rules: the API is denied without a bearer token and allowed
 * with a valid JWT. The decoder is mocked, so no IdP is needed.
 */
@WebMvcTest
@Import(SecurityConfig.class)
class SecurityRulesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RagService ragService;

    @MockitoBean
    IngestionService ingestionService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void deniesApiWithoutToken() throws Exception {
        mvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsApiWithValidJwt() throws Exception {
        mvc.perform(post("/chat").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"hi\"}"))
                .andExpect(status().isOk());
    }
}
