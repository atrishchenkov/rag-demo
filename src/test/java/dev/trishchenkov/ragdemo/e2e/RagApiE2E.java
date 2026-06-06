package dev.trishchenkov.ragdemo.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Black-box acceptance tests against a RUNNING instance (the dev-profile compose stack).
 * Run with: {@code docker compose up -d --build} then {@code mvn -Pe2e verify -De2e.baseUrl=http://localhost:8080}.
 */
class RagApiE2E {

    @BeforeAll
    static void baseUri() {
        RestAssured.baseURI = System.getProperty("e2e.baseUrl", "http://localhost:8080");
    }

    @Test
    void healthIsUp() {
        given().get("/actuator/health")
                .then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void ingestsText() {
        given().contentType(ContentType.JSON)
                .body("{\"text\":\"Spring Boot is a Java framework for stand-alone apps.\",\"metadata\":{\"category\":\"e2e\"}}")
                .post("/documents")
                .then().statusCode(200).body("chunksIndexed", greaterThanOrEqualTo(1));
    }

    @Test
    void ingestsUploadedFile() throws IOException {
        Path file = Files.createTempFile("rag-e2e", ".txt");
        Files.writeString(file, "E2E uploaded content about widgets and pricing.");
        try {
            given().multiPart("file", file.toFile())
                    .post("/documents/file")
                    .then().statusCode(200).body("chunksIndexed", greaterThanOrEqualTo(1));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void ingestsFromUrl() {
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://example.com\"}")
                .post("/documents/url")
                .then().statusCode(200).body("chunksIndexed", greaterThanOrEqualTo(1));
    }

    @Test
    void chatReturnsAnswerAndSources() {
        given().contentType(ContentType.JSON)
                .body("{\"question\":\"What is Spring Boot?\"}")
                .post("/chat")
                .then().statusCode(200)
                .body("answer", notNullValue())
                .body("sources", notNullValue());
    }

    @Test
    void chatStreamReturns200() {
        given().contentType(ContentType.JSON)
                .body("{\"question\":\"What is Spring Boot?\"}")
                .post("/chat/stream")
                .then().statusCode(200);
    }

    @Test
    void unmatchedFilterReturnsNoContextAnswer() {
        given().contentType(ContentType.JSON)
                .body("{\"question\":\"anything\",\"filter\":\"category == 'no_such_xyz'\"}")
                .post("/chat")
                .then().statusCode(200)
                .body("answer", containsString("don't have any indexed"));
    }

    @Test
    void deletesByMetadataFilter() {
        given().queryParam("filter", "category == 'e2e'")
                .delete("/documents")
                .then().statusCode(200);
    }

    @Test
    void rejectsBlankQuestion() {
        given().contentType(ContentType.JSON).body("{\"question\":\"  \"}")
                .post("/chat")
                .then().statusCode(400).body("error", equalTo("invalid_request"));
    }

    @Test
    void rejectsBlankDocumentText() {
        given().contentType(ContentType.JSON).body("{\"text\":\"\"}")
                .post("/documents")
                .then().statusCode(400);
    }

    @Test
    void rejectsDeleteWithoutFilter() {
        given().delete("/documents").then().statusCode(400);
    }

    @Test
    void unknownRouteIs404() {
        given().get("/").then().statusCode(404);
    }

    @Test
    void wrongMethodIs405() {
        given().get("/chat").then().statusCode(405);
    }

    @Test
    void exposesPrometheusMetrics() {
        given().get("/actuator/prometheus")
                .then().statusCode(200).body(containsString("jvm_memory_used_bytes"));
    }
}
