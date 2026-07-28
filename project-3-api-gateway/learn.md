# Project 3 — API Gateway & Traffic Control Service: In-Depth Guide

---

## What Is This Project?

This is a backend-only service that acts as a proxy sitting in front of other HTTP services. Instead of clients calling your backend services directly, they call this gateway. The gateway decides whether to forward the request, block it, or reject it — based on rules you configure.

It is a portfolio project built to practise the concepts behind API gateways: dynamic routing, rate limiting, JWT validation, HTTP forwarding, and request telemetry.

There is no frontend. The gateway exposes a management API (to configure routes) and a metrics endpoint (to see traffic stats). Everything else is traffic flowing through it.

---

## What Does It Actually Do?

1. You register a route — telling the gateway: "any request to `/api/httpbin/**` should be forwarded to `https://httpbin.org`".
2. A client sends a request to the gateway at `/api/gateway/api/httpbin/get`.
3. The gateway looks up the matching route from the database.
4. It checks whether the client IP has exceeded the rate limit for that route.
5. If the route requires auth, it validates the JWT in the `Authorization` header.
6. If all checks pass, it forwards the request to the target URL and returns the response to the client.
7. Every request — including blocked ones — is logged to PostgreSQL with method, path, IP, status code, and latency.
8. You can call `/api/metrics` at any time to see aggregated traffic statistics.

---

## How Routing Works

Routes are stored in the `route_configs` table. Each route has:
- `pathPrefix` — the path prefix to match (e.g. `/api/httpbin`)
- `targetUrl` — where to forward matching requests (e.g. `https://httpbin.org`)
- `rateLimit` — max requests per minute from a single IP
- `requiresAuth` — whether a valid JWT is required

When a request comes in, `GatewayService.resolveRoute()` extracts the first two path segments (e.g. `/api/httpbin` from `/api/httpbin/get`) and queries the database for a route whose `pathPrefix` starts with that string.

If no route matches, a `RouteNotFoundException` is thrown and the client gets a 404.

**How forwarding works:**
The gateway appends the full original request path to the target URL:
```
targetUrl + path
= "https://httpbin.org" + "/api/httpbin/get"
= "https://httpbin.org/api/httpbin/get"
```

This means your route's `pathPrefix` and the path structure of the target service need to align. If the target service has its own path structure, you need to account for that when registering the route.

---

## How Rate Limiting Works

`RateLimiterService` implements a fixed-window rate limiter using a `ConcurrentHashMap<String, ClientBucket>` keyed by client IP address.

Each `ClientBucket` holds:
- `windowStart` — the timestamp when the current window started (milliseconds)
- `counter` — an `AtomicInteger` counting requests in the current window

When `isAllowed(clientIp, limit)` is called:
1. `buckets.compute(clientKey, ...)` atomically gets or creates the bucket for that IP.
2. If the bucket is new, or if `now - windowStart > 60000ms` (the window has expired), a fresh bucket is created starting now.
3. The counter is incremented with `incrementAndGet()`.
4. If the counter is within the limit, the request is allowed. If it exceeds the limit, it is blocked.

This is thread-safe because `ConcurrentHashMap.compute()` is atomic and `AtomicInteger.incrementAndGet()` is atomic. No explicit locks are needed.

**Important limitation:** This is a fixed window, not a sliding window. If the limit is 10 requests per minute, a client could send 10 requests at 00:59 and 10 more at 01:01 — that's 20 requests in 2 seconds, all within their respective windows. A true sliding window would prevent this but requires storing per-request timestamps.

**Another limitation:** Rate limit state lives in memory. If the server restarts, all counters reset. If you ran multiple instances of this gateway, each would have its own counters — a client could hit the limit on each instance separately. A production system would use Redis for shared, persistent rate limit state.

---

## How JWT Validation Works

Routes can be marked `requiresAuth = true`. When a request hits such a route, `GatewayFilter` checks the `Authorization` header before forwarding.

The validation is done inline in the filter using the jjwt library:

```java
Jwts.parserBuilder()
    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
    .build()
    .parseClaimsJws(token);
```

If this throws any exception (expired, invalid signature, malformed), the token is invalid and the request is rejected with a 401.

The gateway does not issue JWTs — it only validates them. The JWT secret must match whatever system issued the token. It is configured in `application.properties`.

---

## How HTTP Forwarding Works

`GatewayFilter` uses Spring's `RestTemplate` to forward the request to the target service.

It copies all headers from the incoming request, constructs the target URL, and calls:

```java
restTemplate.exchange(targetUrl, HttpMethod.valueOf(method), entity, String.class)
```

The response body (as a String) and status code are written back to the original HTTP response. The client receives whatever the target service returned.

The gateway currently forwards GET requests well. For POST/PUT requests with a body, the body is not read from the incoming request and forwarded — this is a known limitation of the current implementation.

---

## How Metrics Work

Every request that passes through `GatewayFilter` is logged to the `request_logs` table, including blocked requests (rate limited, auth rejected, route not found). The log entry contains method, path, client IP, HTTP status code, and latency in milliseconds.

`MetricsService` queries this table to produce:
- `totalRequests` — total row count
- `successCount` — rows where status < 400
- `errorCount` — rows where status >= 400
- `rateLimitedCount` — rows where status = 429
- `averageLatencyMs` — average of the latency column

These are computed with JPQL aggregate queries in `RequestLogRepository`.

---

## How the Code Is Organised

```
com.apigateway
├── config/
│   └── AppConfig.java          — creates the RestTemplate bean
├── controller/
│   ├── RouteController.java    — POST/GET/DELETE /api/routes
│   └── MetricsController.java  — GET /api/metrics
├── domain/
│   ├── RouteConfig.java        — JPA entity: pathPrefix, targetUrl, rateLimit, requiresAuth
│   └── RequestLog.java         — JPA entity: method, path, clientIp, statusCode, latencyMs
├── dto/
│   ├── RouteRequest.java       — input for creating a route
│   ├── RouteConfigResponse.java
│   └── MetricsResponse.java    — totalRequests, successCount, errorCount, rateLimitedCount, avgLatency
├── exception/
│   ├── RateLimitExceededException
│   ├── RouteNotFoundException
│   └── GlobalExceptionHandler
├── filter/
│   └── GatewayFilter.java      — the core: intercepts all traffic, runs rate limit + auth + forward
├── repository/
│   ├── RouteConfigRepository   — findByPathPrefixStartingWith
│   └── RequestLogRepository    — aggregate queries for metrics
└── service/
    ├── GatewayService.java     — resolves route + calls rate limiter
    ├── RateLimiterService.java — fixed-window per-IP rate limiting
    └── MetricsService.java     — aggregates request_logs into MetricsResponse
```

---

## The Request Flow Step by Step

Here is exactly what happens when a client sends `GET /api/gateway/api/httpbin/get`:

1. `GatewayFilter.doFilter()` is called (it is a servlet `Filter` with `@Order(1)`).
2. The path `/api/gateway/api/httpbin/get` does not start with `/api/metrics` or `/api/routes`, so it is not let through directly.
3. `gatewayService.resolveRoute("/api/gateway/api/httpbin/get", "127.0.0.1")` is called.
4. Inside `GatewayService`, `extractPrefix` splits the path and returns `/api/gateway`.
5. The database is queried for a route with `pathPrefix` starting with `/api/gateway`.
6. If found, `rateLimiterService.isAllowed("127.0.0.1", route.getRateLimit())` is called.
7. If rate limit is not exceeded, the route is returned.
8. Back in `GatewayFilter`, if `requiresAuth` is true, the JWT is validated.
9. The target URL is constructed: `route.getTargetUrl() + path`.
10. `RestTemplate.exchange()` sends the request to the target.
11. The response is written back to the client.
12. The request is logged to `request_logs`.

---

## How to Run It

### Prerequisites
- Java 17
- Maven 3.9+
- Docker (for PostgreSQL)

### Step 1 — Start PostgreSQL

From the root `bubu` folder:

```bash
docker compose up -d
```

### Step 2 — Start the Gateway

```bash
cd project-3-api-gateway/backend
mvn spring-boot:run
```

Gateway starts on http://localhost:8083

On Windows without Maven on PATH:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-17'
& 'C:\path\to\maven\apache-maven-3.9.6\bin\mvn.cmd' spring-boot:run
```

### Step 3 — Register a Route

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

### Step 4 — Send a Request Through the Gateway

```bash
curl http://localhost:8083/api/gateway/api/httpbin/get
```

This forwards to `https://httpbin.org/api/gateway/api/httpbin/get` and returns the response.

### Step 5 — Check Metrics

```bash
curl http://localhost:8083/api/metrics
```

Response:
```json
{
  "totalRequests": 5,
  "successCount": 4,
  "errorCount": 1,
  "averageLatencyMs": 198.6,
  "rateLimitedCount": 0
}
```

### Step 6 — Test Rate Limiting

Send more than 10 requests in a minute to the same route. The 11th request returns:
```json
{"error": "Rate limit exceeded"}
```
with HTTP status 429.

### Running the Tests

```bash
cd project-3-api-gateway/backend
mvn test
```

9 tests — 9 passing.
