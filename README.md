# Portfolio — Backend & Full-Stack Projects

A portfolio of 4 projects demonstrating backend architecture, full-stack development, systems thinking, cloud engineering, and practical AI.

---

## Projects

| # | Project | Stack | Port(s) | Status |
|---|---------|-------|---------|--------|
| 1 | [Cloud Resource Management Platform](#1-cloud-resource-management-platform) | Spring Boot, PostgreSQL, React | 8081 / 3001 | ✅ Complete |
| 2 | [Collaborative Workflow Management System](#2-collaborative-workflow-management-system) | Spring Boot, PostgreSQL, WebSockets, React | 8082 / 3002 | ✅ Complete |
| 3 | [API Gateway & Traffic Control Service](#3-api-gateway--traffic-control-service) | Spring Boot, PostgreSQL | 8083 | ✅ Complete |
| 4 | [Codebase Q&A Assistant](#4-codebase-qa-assistant) | Python, FastAPI, FAISS, Ollama | 8000 | 🔄 In Progress |

---

## Prerequisites

- Java 17 (Zulu or similar)
- Maven 3.9+
- Docker & Docker Compose
- Python 3.12
- [Ollama](https://ollama.com) (for Project 4)

---

## Shared Infrastructure

PostgreSQL runs via Docker Compose (required for Projects 1–3):

```bash
docker compose up -d
```

---

## 1. Cloud Resource Management Platform

Manage cloud resources (servers, databases, storage) through a web UI instead of the AWS console.

**Features:** JWT auth, role-based access (ADMIN / OPERATOR / VIEWER), resource state machine, audit log, 25 tests passing.

```bash
# Backend
cd cloud-resource-platform
mvn spring-boot:run

# Frontend
cd cloud-resource-ui
npm install && npm run dev
```

---

## 2. Collaborative Workflow Management System

Jira-like task management with real-time updates via WebSockets.

**Features:** Two-level RBAC, Kanban task board, WebSocket broadcasts, 32 tests passing.

```bash
# Backend
cd workflow-management-system
mvn spring-boot:run

# Frontend
cd workflow-ui
npm install && npm run dev
```

---

## 3. API Gateway & Traffic Control Service

A backend service that sits in front of other services — routing, rate limiting, JWT auth, and request logging.

**Features:** Token bucket rate limiting per client IP, HTTP forwarding, request logging to PostgreSQL, metrics endpoint, 9 tests passing.

```bash
cd api-gateway-service
mvn spring-boot:run
```

---

## 4. Codebase Q&A Assistant

Point it at any project folder — it scans, chunks, embeds, and lets you ask questions about the codebase in plain English.

**Features:** Semantic code search via FAISS, local LLM via Ollama (llama3.2 + nomic-embed-text), 37/39 tests passing.

```bash
# Start Ollama first
ollama serve

cd codebase-qa
cp .env.example .env   # add your GROQ_API_KEY if using Groq
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

API endpoints: `POST /index`, `POST /query`, `GET /status`, `DELETE /index`

---

## What's Next

- [ ] Fix 2 remaining scanner tests in Project 4
- [ ] Run Project 4 end-to-end with real Ollama models
- [ ] Add Dockerfiles to all 4 projects
- [ ] Add GitHub Actions CI/CD pipelines
- [ ] Deploy to AWS (ECS + RDS + ECR)
