# API Gateway & Traffic Control Service

A programmable API gateway implementing fixed-window rate limiting per client IP, JWT validation, HTTP forwarding, and request telemetry — demonstrating the traffic control layer that sits between clients and microservices in production systems.

---

## Problem & Solution

Microservices exposed directly to clients have no shared layer for rate limiting, auth enforcement, or observability. Every service reimplements the same concerns inconsistently. This gateway centralises those concerns: routes are registered dynamically, rate limits are configured per route, JWT validation is enforced where required, and every request is logged to PostgreSQL for metrics and debugging — without the upstream service knowing any of this happened.

---

## Architecture

```
Client Request
      │
      ▼
GatewayFilter  ──── intercepts all traffic at /api/gateway/**
      │
      ▼
GatewayService
      │
   ┌──┴──────────────────┐
   ▼                     ▼
RouteConfigRepo     RateLimiterService
(resolve route)     (ConcurrentHashMap per IP)
      │                  │
      │            ┌─────┴──────┐
      │            │ allowed?   │
      │            ▼            ▼
      │          forward      429 + log
      ▼
RestTemplate  ──── HTTP forward to target URL
      │
      ▼
RequestLogRepo  ──── method, path, IP, status, latency → PostgreSQL
      │
      ▼
MetricsService  ──── aggregates logs → /api/metrics
```

---

## Engineering Highlights

**Fixed-window rate limiting**
`RateLimiterService` uses a `ConcurrentHashMap<String, ClientBucket>` keyed by client IP. Each bucket holds an `AtomicInteger` counter and a window start timestamp. On each request, the bucket is atomically computed — if the window has expired, a new bucket is created; otherwise the counter is incremented and checked against the route's configured limit. Thread-safe without locks.

**Dynamic route registration**
Routes are stored in PostgreSQL and resolved at request time. No restart required to add, update, or remove a route. Each route carries its own rate limit, target URL, and auth requirement.

**JWT validation at the gateway**
Routes marked `requiresAuth=true` have their `Authorization` header validated before forwarding. The upstream service receives the request only if the token is valid — auth is enforced once, centrally.

**Request telemetry**
Every request — including rate-limited and rejected ones — is logged to `request_log` with method, path, client IP, response status, and latency. The `/api/metrics` endpoint aggregates this into total requests, success count, error count, rate-limited count, and average latency.

**HTTP forwarding via RestTemplate**
The gateway forwards the original request (method, headers, body) to the target URL and returns the upstream response to the client. The client is unaware it's talking to a proxy.

---

## Design Decisions & Trade-offs

**In-memory rate limiter vs. Redis** — `ConcurrentHashMap` with `AtomicInteger` is sufficient for a single-instance gateway and has zero infrastructure overhead. The trade-off is that rate limit state is lost on restart and doesn't work across multiple instances. A production system would use Redis with atomic increment and TTL.

**Fixed window, not sliding window** — The implementation resets the counter when `now - windowStart > windowMs`. This is a fixed window, not a sliding window or true token bucket. It can allow up to 2x the configured limit at window boundaries. A true sliding window would require storing per-request timestamps; a token bucket would require tracking token refill rate. The fixed window is simpler and sufficient for demonstrating the concept.

**RestTemplate over WebClient** — RestTemplate is synchronous and simpler to reason about for a forwarding proxy. WebClient would be appropriate if the gateway needed to handle high concurrency with non-blocking I/O. For this use case, the synchronous model is clearer.

**Logging rejected requests** — Rate-limited and auth-rejected requests are still logged to `request_log`. This means the metrics endpoint accurately reflects all traffic including blocked requests, which is how production gateways (e.g. AWS API Gateway) report metrics.

---

## API Reference

**Route Management**

| Method | Endpoint              | Description                        |
|--------|-----------------------|------------------------------------|
| POST   | `/api/routes`         | Register a new route               |
| GET    | `/api/routes`         | List all registered routes         |
| DELETE | `/api/routes/{id}`    | Remove a route                     |

**Gateway**

| Method | Endpoint                    | Description                              |
|--------|-----------------------------|------------------------------------------|
| ANY    | `/api/gateway/**`           | Forward request to matched route target  |

**Observability**

| Method | Endpoint        | Description                                          |
|--------|-----------------|------------------------------------------------------|
| GET    | `/api/metrics`  | Total, success, error, rate-limited, avg latency     |

**Example — register a route:**
```bash
curl -X POST http://localhost:8083/api/routes \
  -H "Content-Type: application/json" \
  -d '{
    "pathPrefix": "/api/httpbin",
    "targetUrl": "https://httpbin.org",
    "rateLimit": 10,
    "requiresAuth": false
  }'
```

**Example — forward a request:**
```bash
curl http://localhost:8083/api/gateway/api/httpbin/get
```

**Example — metrics:**
```json
{
  "totalRequests": 47,
  "successCount": 43,
  "errorCount": 1,
  "rateLimitedCount": 3,
  "averageLatencyMs": 212.4
}
```

---

## Stack

| Layer    | Technology                              |
|----------|-----------------------------------------|
| Backend  | Java 17, Spring Boot 3.2                |
| Database | PostgreSQL 15 (Docker)                  |
| HTTP     | RestTemplate (forwarding)               |
| Testing  | JUnit 5, Mockito, Testcontainers        |

---

## Running Locally

**Prerequisites:** Java 17, Maven 3.9+, Docker

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Start gateway
cd project-3-api-gateway/backend
mvn spring-boot:run
```

Gateway: http://localhost:8083

---

## Tests

```bash
cd project-3-api-gateway/backend
mvn test
```

9 tests — 9 passing.

Covers: route resolution, rate limit enforcement (429 after limit), auth rejection (401 without token), HTTP forwarding, metrics aggregation, unknown route handling.
