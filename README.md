# Portfolio — Backend & Full-Stack Projects

4 projects demonstrating backend architecture, full-stack development, systems thinking, and practical AI — built to production-oriented standards with real tests, real AWS integration, and documented design decisions.

---

## Projects

| # | Project | Stack | Port(s) |
|---|---------|-------|---------|
| 1 | [Cloud Resource Management Platform](#1-cloud-resource-management-platform) | Java, Spring Boot, PostgreSQL, AWS SDK v2, React | 8081 / 3001 |
| 2 | [Collaborative Workflow Management System](#2-collaborative-workflow-management-system) | Java, Spring Boot, PostgreSQL, WebSockets, React | 8082 / 3002 |
| 3 | [API Gateway & Traffic Control Service](#3-api-gateway--traffic-control-service) | Java, Spring Boot, PostgreSQL | 8083 |
| 4 | [Codebase Q&A Assistant](#4-codebase-qa-assistant) | Python, FastAPI, TF-IDF, Groq | 8000 |

---

## Prerequisites

- Java 17
- Maven 3.9+
- Docker & Docker Compose
- Node.js (for Projects 1 and 2 frontends)
- Python 3.12 (for Project 4)
- Groq API key (for Project 4 — free at https://console.groq.com)

---

## Shared Infrastructure

Projects 1–3 share a PostgreSQL instance running via Docker Compose at the repo root:

```bash
docker compose up -d
```

Each project also has its own `docker-compose.yml` if you want to run it in isolation.

---

## 1. Cloud Resource Management Platform

A provisioning platform with enforced resource lifecycle rules, role-scoped API access, and an append-only audit trail — built to practise the access control and auditability patterns used in real cloud management systems.

**Highlights:**
- JWT authentication with three-role RBAC — `ADMIN`, `OPERATOR`, `VIEWER` — enforced at both the controller and service layer
- Resource state machine: `PENDING → RUNNING → STOPPED → TERMINATED`, with illegal transitions rejected before any persistence occurs
- Append-only audit log — every create, stop, terminate, and status update writes a new immutable row attributed to the acting user
- AWS SDK v2 integration — EC2 instances and S3 buckets are provisioned, stopped, and terminated via real AWS API calls
- 25 tests passing

```bash
# Start PostgreSQL
docker compose up -d

# Backend
cd project-1-cloud-resource/backend
mvn spring-boot:run

# Frontend (separate terminal)
cd project-1-cloud-resource/frontend
npm install
npm run dev
```

Backend: http://localhost:8081 — Frontend: http://localhost:3001

---

## 2. Collaborative Workflow Management System

A real-time task management platform with two-level RBAC and WebSocket-broadcast state transitions — demonstrating the auth and consistency patterns common in multi-tenant SaaS systems.

**Highlights:**
- Two-level RBAC: system role (`SYSTEM_ADMIN` / `SYSTEM_MEMBER`) controls workspace creation; workspace role (`ADMIN` / `MEMBER`) controls member and task management — checked independently on every request
- WebSocket broadcasts via STOMP — every task status change is pushed to all connected clients in that workspace in real time
- Workspace-scoped visibility — members only see workspaces they belong to, enforced at the query level
- JWT carries system role; workspace role is always resolved from the database
- 32 tests passing (18 integration with H2, 14 unit with Mockito)

```bash
# Start PostgreSQL
docker compose up -d

# Backend
cd project-2-workflow/backend
mvn spring-boot:run

# Frontend (separate terminal)
cd project-2-workflow/frontend
npm install
npm run dev
```

Backend: http://localhost:8082 — Frontend: http://localhost:3002

---

## 3. API Gateway & Traffic Control Service

A programmable API gateway with fixed-window rate limiting per client IP, JWT validation, HTTP forwarding, and request telemetry — demonstrating the traffic control layer that sits between clients and microservices.

**Highlights:**
- Fixed-window rate limiter using `ConcurrentHashMap` + `AtomicInteger` — thread-safe without locks, configurable limit per route
- Dynamic route registration — routes are stored in PostgreSQL and resolved at request time, no restart required
- JWT validation enforced at the gateway before forwarding — upstream services receive only authenticated requests
- Every request logged to PostgreSQL with method, path, client IP, status code, and latency — aggregated at `GET /api/metrics`
- 9 tests passing

```bash
# Start PostgreSQL
docker compose up -d

cd project-3-api-gateway/backend
mvn spring-boot:run
```

Gateway: http://localhost:8083

---

## 4. Codebase Q&A Assistant

A RAG pipeline that chunks source code by function and class boundaries, indexes it with TF-IDF vectors, and grounds LLM answers in retrieved context — built from scratch without a managed RAG service.

**Highlights:**
- Language-aware boundary detection using regex patterns for Java, Python, JS/TS, Kotlin, C/C++ — chunks follow function and class boundaries rather than arbitrary line counts
- Custom TF-IDF vector store built with numpy — no external vector database, cosine similarity search over all indexed chunks
- Groq LLM inference (`llama-3.1-8b-instant`) with retrieved context injected into the prompt — answers are grounded in actual code, not generated from model weights
- Source citations returned with every answer — file path, function name, and line numbers
- 64 tests passing

```bash
cd project-4-codebase-qa/app
cp .env.example .env   # add GROQ_API_KEY
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

API: http://localhost:8000 — Docs: http://localhost:8000/docs

Endpoints: `POST /index`, `POST /query`, `GET /status`, `DELETE /index`
