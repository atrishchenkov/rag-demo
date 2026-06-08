# RAG Service — Spring Boot · Spring AI · Claude / OpenAI · pgvector

[![CI](https://github.com/atrishchenkov/rag-demo/actions/workflows/ci.yml/badge.svg)](https://github.com/atrishchenkov/rag-demo/actions/workflows/ci.yml)

A small but **production-shaped** Retrieval-Augmented Generation backend: ingest documents (text, PDF, URLs), then ask questions and get answers grounded in those documents — with sources, streaming, metadata-scoped retrieval, and the cross-cutting concerns a real service needs (security, observability, resilience, containerization).

Built on a standard Java backend stack with a thin, provider-agnostic LLM layer on top — no Python, no notebooks.

## What it does

```
   text / PDF / URL ──▶┌──────────────┐   embeddings    ┌──────────────────┐
   POST /documents*    │  ingestion   │──(local ONNX)──▶│  PostgreSQL +    │
                       │  + chunking  │                 │  pgvector store  │
                       └──────────────┘                 └────────┬─────────┘
                                                          top-K   │ (+ metadata filter)
   POST /chat        ┌──────────────┐  grounded prompt  ┌────────▼─────────┐
   POST /chat/stream▶│ retrieve top │─────────────────▶ │  Claude / OpenAI │
   answer + sources ◀│ K + augment  │◀──── answer ───── │  via Spring AI   │
                     └──────────────┘                   └──────────────────┘
```

1. **Ingest** — content is split into chunks, embedded **locally** (ONNX `all-MiniLM-L6-v2`, 384-dim — no embedding API cost), and stored in pgvector with its metadata.
2. **Ask** — the question is embedded, the top-K most similar chunks (optionally filtered by metadata) are retrieved, injected into a grounded prompt, and answered by the configured LLM. The answer is constrained to the retrieved context (it says "I don't know" rather than hallucinating).

## API

All endpoints require a valid OAuth2 JWT (see [Security](#security)) — except in the local `dev` profile.

| Method & path | Purpose |
|---|---|
| `POST /documents` | Ingest raw text (optional `metadata`) |
| `POST /documents/url` | Ingest a web page / remote document by URL (Apache Tika) |
| `POST /documents/file` | Ingest an uploaded file — PDF, DOCX, HTML… (multipart) |
| `DELETE /documents?filter=…` | Delete indexed chunks by metadata filter |
| `POST /chat` | Ask a question → grounded answer + sources (optional `filter`) |
| `POST /chat/stream` | Same, streamed token-by-token over SSE |
| `GET /actuator/health`, `/actuator/prometheus` | Health probes + metrics (open) |

## Stack

| Concern | Choice |
|---|---|
| Language / framework | Java 17, Spring Boot 3.4 |
| LLM orchestration | Spring AI 1.0 (`ChatClient`, `VectorStore`) |
| LLM provider | **Anthropic Claude** (default) or **OpenAI** — selected by config, no code change |
| Embeddings | Local ONNX `all-MiniLM-L6-v2` (offline, no API key) |
| Vector store | PostgreSQL + pgvector (HNSW, cosine) |
| Document parsing | Apache Tika (PDF / DOCX / HTML / …) |
| Security | OAuth2 Resource Server (JWT), stateless |
| Resilience | Resilience4j circuit breaker + fallback on the LLM call |
| Observability | Actuator, Micrometer → Prometheus, OpenTelemetry tracing |
| Containerization | Multi-stage Dockerfile + Helm chart (k8s probes, Prometheus scrape) |
| Static analysis | `javac -Xlint:all -Werror` + SpotBugs (build fails on any finding) |
| Tests | JUnit 5, Mockito, Spring Security Test, Testcontainers (real pgvector) |

## Project structure

Layered packages, thin web layer delegating to services:

```
ragdemo/
├── RagDemoApplication   bootstrap
├── web/                 ChatController, IngestionController, ApiExceptionHandler (400/500, no stack-trace leak)
├── service/             RagService (retrieve top-K + filter → grounded generation, sync + streaming),
│                        IngestionService (text/URL/file → chunk → embed → store, delete)
├── dto/                 request/response records (Answer, Question, IngestRequest, IngestResponse, UrlRequest)
├── config/             RagProperties (externalized tunables — rag.top-k)
└── security/           SecurityConfig (OAuth2 JWT) + DevSecurityConfig (open, "dev" profile)
```

## Run it

### Full stack — one command

`docker compose up -d --build` brings up the entire environment:

| Service | URL | Notes |
|---|---|---|
| rag-demo app | http://localhost:8080 | runs in the `dev` profile (API open for easy testing) |
| PostgreSQL + pgvector | localhost:5432 | vector store |
| Prometheus | http://localhost:9090 | scrapes the app's `/actuator/prometheus` |
| Grafana | http://localhost:3000 | anonymous admin; Prometheus datasource provisioned |
| Keycloak | http://localhost:8081 | realm `rag` pre-imported (console admin/admin) |

```bash
# Optional — without a real key, /chat degrades gracefully via the circuit breaker.
export ANTHROPIC_API_KEY=sk-ant-...        # PowerShell: $env:ANTHROPIC_API_KEY="sk-ant-..."
docker compose up -d --build
```

Then exercise it ([Try it](#try-it-dev-profile)). Tear everything down with `docker compose down -v`.

### Local run (app from source, deps in Docker)

```bash
docker compose up -d postgres
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # 'dev' opens the API; first start pulls the ~90MB embedding model
```

> **Secured mode (default profile).** Drop the `dev` profile and the API requires an OAuth2 JWT validated against Keycloak. Grab a token from the bundled IdP and call with `Authorization: Bearer <token>`:
> ```bash
> TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=rag-client -d client_secret=rag-secret \
>   http://localhost:8081/realms/rag/protocol/openid-connect/token | jq -r .access_token)
> curl -H "Authorization: Bearer $TOKEN" -X POST localhost:8080/chat ...
> ```

### Try it (dev profile)

```bash
# Ingest text with metadata
curl -X POST localhost:8080/documents -H 'Content-Type: application/json' \
  -d '{"text": "Spring Boot favours convention over configuration and bundles an embedded server.",
       "metadata": {"category": "docs"}}'

# Ingest a PDF / DOCX / HTML file, or a web page by URL
curl -X POST localhost:8080/documents/file -F file=@whitepaper.pdf
curl -X POST localhost:8080/documents/url -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com/article"}'

# Ask a grounded question (optionally scoped by metadata)
curl -X POST localhost:8080/chat -H 'Content-Type: application/json' \
  -d '{"question": "What problem does Spring Boot solve?", "filter": "category == '\''docs'\''"}'
# → {"answer": "...", "sources": ["..."]}

# Stream the answer (Server-Sent Events)
curl -N -X POST localhost:8080/chat/stream -H 'Content-Type: application/json' \
  -d '{"question": "What problem does Spring Boot solve?"}'

# Delete everything tagged category=docs
curl -X DELETE "localhost:8080/documents?filter=category == '\''docs'\''"
```

## Design notes

- **Thin web layer, behavior in services.** Controllers only validate and delegate; `IngestionService` and `RagService` own the domain logic. Each has a single responsibility (SRP).
- **Provider-agnostic by design — no custom abstraction.** Services depend on Spring AI's `VectorStore` and `ChatClient` interfaces. Switching the LLM is one config line — `spring.ai.model.chat: anthropic | openai` — with **zero code change**. We deliberately did *not* wrap Spring AI in hand-rolled "ports": the framework already is the abstraction, and another layer would earn nothing (YAGNI).
- **Local embeddings, remote generation.** Embeddings run in-process via ONNX — cheap, fast, no second vendor or API key. Only answer generation calls out to the LLM.
- **Grounded, not free-form.** The prompt restricts the model to the retrieved context and tells it to admit when the answer isn't there — the guardrail that separates RAG from "ask an LLM and hope".
- **Sources returned with every answer.** Retrieval is explicit so the API returns the exact chunks that informed the answer — auditable and debuggable.

## Production-readiness (cross-cutting)

### Security

Stateless **OAuth2 Resource Server** — every API call requires a valid JWT, validated against your IdP's JWKS (`OAUTH2_JWK_SET_URI`). `/actuator/**` stays open for probes and scraping. The `dev` profile swaps in an open chain for local experimentation; the default profile is secured.

**SSRF guard** — `POST /documents/url` fetches a remote document, so the URL is validated first: only `http`/`https`, and the resolved host must not be loopback, private, link-local, or any-local. This stops a caller (or a crafted document pipeline) from using ingestion to reach internal services or the cloud metadata endpoint.

### Resilience

- **Circuit breaker** (Resilience4j) around the external LLM call — when the model provider is failing or slow, the breaker opens and `RagService` returns a graceful fallback instead of cascading failures.
- **Retry with backoff** — Spring AI retries transient `429`/`5xx` responses (`spring.ai.retry.*`).
- **Graceful empty retrieval** — nothing indexed ⇒ a "no relevant documents" answer, **skipping the LLM call** (saves cost, avoids hallucination).
- **No leaked internals** — unexpected errors are logged and returned as a generic `500`, never a stack trace; bad input is `400`.

### Observability

- **Health** — `/actuator/health` with Kubernetes `liveness` / `readiness` groups.
- **Metrics** — Micrometer → `/actuator/prometheus` (JVM, HTTP, datasource, circuit-breaker metrics).
- **Tracing** — OpenTelemetry via Micrometer Tracing; trace/span IDs flow into logs and export to any OTLP collector (Tempo/Jaeger) by adding `opentelemetry-exporter-otlp` + `management.otlp.tracing.endpoint`.

### Containerization & Kubernetes

- **Full local stack** — `compose.yaml` runs app + pgvector + Prometheus + Grafana + Keycloak together (`docker compose up -d --build`), so metrics, dashboards and the IdP are all live with one command.
- **Multi-stage `Dockerfile`** → layered Spring Boot jar on a slim JRE, running as a **non-root** user.
- **Helm chart** (`helm/rag-demo`) — Deployment + Service, liveness/readiness probes wired to the Actuator endpoints, Prometheus scrape annotations, ConfigMap/Secret split (no secrets in the image), and resource requests/limits.

```bash
docker build -t rag-demo:0.1.0 .
helm install rag ./helm/rag-demo --set secrets.anthropicApiKey=sk-ant-...
```

### Tunable without code changes

- `spring.ai.model.chat` → `anthropic` (default) or `openai`; embedding stays local.
- `spring.ai.anthropic.chat.options.model` → `claude-haiku-4-5` / `claude-sonnet-4-6` / `claude-opus-4-8` (or `spring.ai.openai.chat.options.model`).
- `rag.top-k`, `resilience4j.circuitbreaker.instances.llm.*`, `management.tracing.sampling.probability`.

## Quality gates

Enforced on every build; CI fails on any regression:

- **`javac -Xlint:all -Werror`** — zero compiler warnings.
- **SpotBugs** (effort `Max`) bound to `verify` — build fails on any bug pattern.

```bash
./mvnw verify     # compile (-Werror) + tests + SpotBugs
```

> VS Code's Eclipse JDT *optional* null-analysis flags false positives against Spring's `@NonNullApi` packages; it is disabled in the workspace settings so the editor matches the authoritative build gates above.

## Tests

A full pyramid — unit and slice tests with no infrastructure, integration tests against a real database, and black-box E2E against the running app.

```bash
./mvnw verify                                   # unit + slice + Testcontainers integration + SpotBugs
./mvnw -Pe2e verify -De2e.baseUrl=http://localhost:8080   # E2E acceptance against a RUNNING stack
./scripts/smoke-test.sh                          # quick stack + infra liveness (curl)
```

| Test | Type | Needs Docker? | Covers |
|---|---|---|---|
| `RagServiceTest` | unit | No | empty-retrieval fallback skips the LLM |
| `ChatControllerTest` | `@WebMvcTest` slice | No | HTTP contract + validation (`400` on blank input) |
| `SecurityRulesTest` | `@WebMvcTest` + Spring Security Test | No | API denied without JWT, allowed with a valid one |
| `RagRetrievalIntegrationTest` | Testcontainers | Yes* | real pgvector: embed → store → semantic retrieval, no mocks |
| `MetadataFilterIntegrationTest` | Testcontainers | Yes* | metadata-scoped retrieval + delete-by-filter on real pgvector |
| `ChatFlowIntegrationTest` | Testcontainers | Yes* | full `/chat` + `/chat/stream` over real pgvector, LLM stubbed at the `ChatModel` boundary |
| `RagApiE2E` | REST Assured (profile `e2e`) | running app | every endpoint black-box: ingest/chat/stream/delete, validation, routing, metrics |
| `scripts/smoke-test.sh` | bash + curl | running stack | 24 checks incl. Prometheus target up, Grafana, Keycloak JWT |

\* Testcontainers tests skip cleanly (`@Testcontainers(disabledWithoutDocker = true)`) when Docker isn't installed, so `mvn verify` stays green everywhere and runs the full path in CI.

The **LLM is never called against the live API in tests** — unit/integration tests stub it at the `ChatModel` bean; the E2E suite runs against the dev stack where a missing key simply trips the circuit breaker into the graceful fallback. So everything we wrote — retrieval, metadata filtering, prompt assembly, response mapping, streaming, security rules, controllers — is exercised end-to-end without a paid API key. CI runs the unit/integration gate and the full-stack E2E as separate jobs (see `.github/workflows/ci.yml`).
