# Portfolio — Backend & Full-Stack Projects

4 projects demonstrating backend architecture, full-stack development, systems thinking, and practical AI.

---

## Projects

| # | Project | Stack | Port(s) | Status |
|---|---------|-------|---------|--------|
| 1 | [Cloud Resource Management Platform](#1-cloud-resource-management-platform) | Spring Boot, PostgreSQL, AWS SDK, React | 8081 / 3001 | ✅ Complete |
| 2 | [Collaborative Workflow Management System](#2-collaborative-workflow-management-system) | Spring Boot, PostgreSQL, WebSockets, React | 8082 / 3002 | ✅ Complete |
| 3 | [API Gateway & Traffic Control Service](#3-api-gateway--traffic-control-service) | Spring Boot, PostgreSQL | 8083 | ✅ Complete |
| 4 | [Codebase Q&A Assistant](#4-codebase-qa-assistant) | Python, FastAPI, TF-IDF, Groq | 8000 | 🔄 In Progress |

---

## Prerequisites

- Java 17
- Maven 3.9+
- Docker & Docker Compose
- Python 3.12
- Groq API key (for Project 4 — free at https://console.groq.com)

---

## Shared Infrastructure

PostgreSQL runs via Docker Compose (required for Projects 1–3):

```bash
docker compose up -d
```

---

## 1. Cloud Resource Management Platform

A provisioning platform with enforced resource lifecycle rules, role-scoped API access, and an append-only audit trail — built to practise the access control and auditability patterns used in real cloud management systems.

**Highlights:** JWT auth, three-role RBAC (ADMIN / OPERATOR / VIEWER), resource state machine (PENDING → RUNNING → STOPPED → TERMINATED), AWS SDK v2 integration (EC2 + S3), append-only audit log, 25 tests passing.

```bash
# Backend
cd project-1-cloud-resource/backend
mvn spring-boot:run

# Frontend (separate terminal)
cd project-1-cloud-resource/frontend
npm install
npm run dev
```

---

## 2. Collaborative Workflow Management System

A real-time task management platform with two-level RBAC and WebSocket-broadcast state transitions — demonstrating the auth and consistency patterns common in multi-tenant SaaS systems.

**Highlights:** Two-level RBAC (system role + workspace role), task status validation, WebSocket broadcasts via STOMP on every status change, workspace-scoped visibility, 32 tests passing.

```bash
# Backend
cd project-2-workflow/backend
mvn spring-boot:run

# Frontend (separate terminal)
cd project-2-workflow/frontend
npm install
npm run dev
```

---

## 3. API Gateway & Traffic Control Service

A programmable API gateway with fixed-window rate limiting per client IP, JWT validation, HTTP forwarding, and request telemetry — demonstrating the traffic control layer that sits between clients and microservices.

**Highlights:** Fixed-window rate limiter using `ConcurrentHashMap` + `AtomicInteger` (thread-safe, no locks), dynamic route registration, JWT validation per route, full request logging to PostgreSQL, metrics endpoint, 9 tests passing.

```bash
cd project-3-api-gateway/backend
mvn spring-boot:run
```

---

## 4. Codebase Q&A Assistant

A RAG pipeline that chunks source code by function and class boundaries, indexes it with TF-IDF vectors, and grounds LLM answers in retrieved context — built from scratch without a managed RAG service.

**Highlights:** Language-aware boundary detection (Java, Python, JS/TS, Kotlin, C/C++), custom TF-IDF vector store with cosine similarity (numpy), Groq LLM inference, source-cited answers with file + line numbers, 37/39 tests passing.

```bash
cd project-4-codebase-qa/app
cp .env.example .env   # add GROQ_API_KEY
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

API: `POST /index`, `POST /query`, `GET /status`, `DELETE /index`

---

## What's Next

- [ ] Fix 2 remaining scanner tests in Project 4 (Windows path separator issue)
- [ ] Add GitHub Actions CI/CD pipelines
- [ ] Deploy to AWS (ECS + RDS + ECR)
