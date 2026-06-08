#!/usr/bin/env bash
#
# Smoke test for the rag-demo full stack (docker compose up -d --build).
# Exercises every API endpoint plus the supporting infrastructure and exits non-zero on any failure.
# Override targets with APP_URL / PROMETHEUS_URL / GRAFANA_URL / KEYCLOAK_URL.

set -u

APP="${APP_URL:-http://localhost:8080}"
PROM="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA="${GRAFANA_URL:-http://localhost:3000}"
KEYCLOAK="${KEYCLOAK_URL:-http://localhost:8081}"

pass=0
fail=0
PASS() { echo "  [PASS] $1"; pass=$((pass + 1)); }
FAIL() { echo "  [FAIL] $1"; fail=$((fail + 1)); }

# check_status <desc> <expected-code> <curl-args...>
check_status() {
  local desc="$1" exp="$2"
  shift 2
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 240 "$@")
  if [ "$code" = "$exp" ]; then PASS "$desc ($code)"; else FAIL "$desc (got $code, want $exp)"; fi
}

# check_body <desc> <substring> <curl-args...>
check_body() {
  local desc="$1" sub="$2"
  shift 2
  local body
  body=$(curl -s --max-time 240 "$@")
  if printf '%s' "$body" | grep -q "$sub"; then PASS "$desc"; else FAIL "$desc (missing '$sub' in: $(printf '%.120s' "$body"))"; fi
}

echo "Waiting for app to become healthy at $APP ..."
# A cold container downloads the ONNX embedding model at startup (~2.5 min), so allow generously.
for _ in $(seq 1 70); do
  curl -sf "$APP/actuator/health" >/dev/null 2>&1 && break
  sleep 3
done

echo "== App: health =="
check_status "GET /actuator/health" 200 "$APP/actuator/health"
check_body   "health status UP" '"status":"UP"' "$APP/actuator/health"
check_body   "readiness UP" 'UP' "$APP/actuator/health/readiness"

echo "== App: ingestion =="
check_body "POST /documents (text)" '"chunksIndexed"' \
  -X POST -H 'Content-Type: application/json' \
  -d '{"text":"Spring Boot is a Java framework for stand-alone apps.","metadata":{"category":"smoke"}}' "$APP/documents"
echo "smoke upload about widgets and pricing" >/tmp/rag-smoke.txt
check_body "POST /documents/file" '"chunksIndexed"' -X POST -F 'file=@/tmp/rag-smoke.txt' "$APP/documents/file"
check_status "POST /documents/url" 200 \
  -X POST -H 'Content-Type: application/json' -d '{"url":"https://example.com"}' "$APP/documents/url"

echo "== App: chat =="
check_status "POST /chat" 200 \
  -X POST -H 'Content-Type: application/json' -d '{"question":"What is Spring Boot?"}' "$APP/chat"
check_body   "POST /chat returns answer" '"answer"' \
  -X POST -H 'Content-Type: application/json' -d '{"question":"What is Spring Boot?"}' "$APP/chat"
check_status "POST /chat/stream" 200 \
  -X POST -H 'Content-Type: application/json' -d '{"question":"What is Spring Boot?"}' "$APP/chat/stream"
check_status "DELETE /documents?filter" 200 \
  -X DELETE "$APP/documents?filter=category%20%3D%3D%20%27smoke%27"
check_body   "POST /chat unmatched filter -> no-context answer" "don't have any indexed" \
  -X POST -H 'Content-Type: application/json' -d '{"question":"anything","filter":"category == '\''nope_xyz'\''"}' "$APP/chat"

echo "== App: validation & routing =="
check_status "POST /chat blank -> 400" 400 \
  -X POST -H 'Content-Type: application/json' -d '{"question":"  "}' "$APP/chat"
check_status "POST /documents blank text -> 400" 400 \
  -X POST -H 'Content-Type: application/json' -d '{"text":""}' "$APP/documents"
check_status "POST /documents/url blank -> 400" 400 \
  -X POST -H 'Content-Type: application/json' -d '{"url":""}' "$APP/documents/url"
check_status "DELETE /documents (no filter) -> 400" 400 -X DELETE "$APP/documents"
check_status "GET /chat (wrong method) -> 405" 405 "$APP/chat"
check_status "POST /chat malformed json -> 400" 400 \
  -X POST -H 'Content-Type: application/json' -d '{bad json' "$APP/chat"
check_status "GET / -> 404 (no route)" 404 "$APP/"

echo "== App: metrics =="
check_body "GET /actuator/prometheus" 'jvm_memory_used_bytes' "$APP/actuator/prometheus"

echo "== Infra: Prometheus =="
check_status "Prometheus healthy" 200 "$PROM/-/healthy"
check_body   "Prometheus scrapes rag-demo (up)" '"health":"up"' "$PROM/api/v1/targets"
check_body   "Prometheus stored up{job=rag-demo}=1" '"1"]' "$PROM/api/v1/query?query=up%7Bjob%3D%22rag-demo%22%7D"

echo "== Infra: Grafana =="
check_status "Grafana health" 200 "$GRAFANA/api/health"

echo "== Infra: Keycloak =="
check_body "Keycloak issues JWT" 'access_token' \
  -X POST -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&client_id=rag-client&client_secret=rag-secret' \
  "$KEYCLOAK/realms/rag/protocol/openid-connect/token"

echo
echo "Result: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
