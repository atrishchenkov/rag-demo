package dev.trishchenkov.ragdemo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestionUrlGuardTest {

    @Test
    void blocksSsrfToInternalAddresses() {
        assertThatThrownBy(() -> IngestionService.requirePublicHttpUrl("http://127.0.0.1/admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IngestionService.requirePublicHttpUrl("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IngestionService.requirePublicHttpUrl("http://10.0.0.5/internal"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> IngestionService.requirePublicHttpUrl("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IngestionService.requirePublicHttpUrl("ftp://example.com/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsPublicHttpUrl() {
        assertThatCode(() -> IngestionService.requirePublicHttpUrl("http://8.8.8.8/")).doesNotThrowAnyException();
    }
}
