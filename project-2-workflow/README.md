# Collaborative Workflow Management System

A real-time collaborative task platform with two-level RBAC, WebSocket-broadcast state transitions, and JWT-embedded roles to eliminate round-trips on the frontend — demonstrating the auth and consistency patterns common in multi-tenant SaaS systems.

---

## Problem & Solution

Task management tools either have flat permission models (everyone can do everything) or overly complex ones that are hard to reason about. This system implements a clean two-level RBAC — system-wide roles control who can create workspaces, workspace-level roles control who can manage members and tasks — with real-time updates pushed to all connected clients the moment a task changes state.

---

## Architecture

```
React UI (port 3002)
        │
   ┌────┴────────────────────┐
   │ REST (HTTP)             │ WebSocket (STOMP)
   ▼                         ▼
JwtAuthFilter           /topic/workspace/{id}
        │                    ▲
        ▼                    │
  Controllers           NotificationService
        │                    │
        ▼                    │
  TaskService ───────────────┘
  WorkspaceService
        │
   ┌────┴────┐
   ▼         ▼
TaskRepo  WorkspaceMemberRepo
        │
        ▼
  PostgreSQL (Docker)
```

---

## Engineering Highlights

**Two-level RBAC**
System level and workspace level are independent:
- `SYSTEM_ADMIN` — can create workspaces
- `SYSTEM_MEMBER` — can be invited to workspaces
- `WORKSPACE_ADMIN` — can invite/remove members, delete workspace
- `WORKSPACE_MEMBER` — can create and move tasks

Roles are checked at the service layer. A `SYSTEM_MEMBER` cannot create a workspace regardless of their workspace role.

**JWT-embedded roles**
The system role (`SYSTEM_ADMIN` / `SYSTEM_MEMBER`) is encoded in the JWT at login. The frontend reads the role directly from the token to determine what UI elements to show without an extra API call. Workspace-level role is checked server-side against the `workspace_member` table on each request.

**WebSocket broadcasts on state change**
When a task transitions state, `NotificationService` broadcasts a `TaskNotification` to `/topic/workspace/{id}` via STOMP. All connected clients in that workspace receive the update in real time without polling.

**Task status validation**
Before a task status update is saved, `TaskStateTransitionValidator.isValid(from, to)` is called. The rule is simple: the new status must be different from the current one. There is no enforced ordering — you can move a task from `TODO` directly to `DONE`, or back from `DONE` to `TODO`. The validator only prevents a no-op (setting a task to the status it already has), which would throw `InvalidTaskTransitionException`.

**Workspace-scoped visibility**
Members only see workspaces they belong to. Workspace queries are filtered by membership, not by ownership — supporting the multi-member model correctly.

---

## Design Decisions & Trade-offs

**STOMP over raw WebSockets** — STOMP gives topic-based pub/sub on top of WebSocket, so the server broadcasts to a workspace topic and all subscribers receive it. The alternative (tracking connections per workspace manually) would be significantly more complex with no benefit at this scale.

**Roles in JWT vs. DB lookup per request** — Embedding roles in the token avoids a DB lookup on every request. The trade-off is stale roles until token expiry. Mitigated by using short-lived tokens; a production system would add a revocation mechanism for immediate role changes.

**H2 for integration tests** — Integration tests use H2 in-memory rather than Testcontainers to keep the test suite fast. The trade-off is that PostgreSQL-specific behaviour isn't tested at the integration level. Unit tests cover the business logic; a staging environment would catch DB-specific issues.

**`@Modifying @Transactional` for member removal** — Removing a workspace member uses a JPQL delete query rather than load-then-delete to avoid loading the entity unnecessarily. This requires explicit `@Modifying` and `@Transactional` annotations, which are easy to forget — documented here as a known footgun.

---

## API Reference

| Method | Endpoint                                        | Role Required          | Description                        |
|--------|-------------------------------------------------|------------------------|------------------------------------|
| POST   | `/api/auth/register`                            | Public                 | Register a new user                |
| POST   | `/api/auth/login`                               | Public                 | Authenticate, returns JWT          |
| POST   | `/api/workspaces`                               | SYSTEM_ADMIN           | Create a workspace                 |
| GET    | `/api/workspaces`                               | Authenticated          | List workspaces for current user   |
| POST   | `/api/workspaces/{id}/members`                  | WORKSPACE_ADMIN        | Invite a member                    |
| DELETE | `/api/workspaces/{id}/members/{username}`       | WORKSPACE_ADMIN        | Remove a member                    |
| GET    | `/api/users`                                    | Authenticated          | List all org users (for invite UI) |
| POST   | `/api/tasks`                                    | WORKSPACE_MEMBER+      | Create a task                      |
| GET    | `/api/tasks/workspace/{workspaceId}`            | Workspace member       | List tasks in a workspace          |
| PATCH  | `/api/tasks/{id}/status`                        | Workspace member       | Update task status                 |
| DELETE | `/api/tasks/{id}`                               | WORKSPACE_ADMIN        | Delete a task                      |

**WebSocket endpoint:** `ws://localhost:8082/ws`  
**Subscribe to workspace updates:** `/topic/workspace/{workspaceId}`

**Example — transition a task:**
```bash
curl -X PUT http://localhost:8082/api/tasks/{id}/status \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"status": "IN_PROGRESS"}'
```

---

## Stack

| Layer    | Technology                                      |
|----------|-------------------------------------------------|
| Backend  | Java 17, Spring Boot 3.2, Spring Security       |
| Auth     | JWT (jjwt 0.11.5)                               |
| Realtime | Spring WebSocket, STOMP                         |
| Database | PostgreSQL 15 (Docker)                          |
| Frontend | React, Vite                                     |
| Testing  | JUnit 5, Mockito, H2 (integration), Instancio   |

---

## Running Locally

**Prerequisites:** Java 17, Maven 3.9+, Docker, Node.js

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Start backend
cd project-2-workflow/backend
mvn spring-boot:run

# 3. Start frontend (separate terminal)
cd project-2-workflow/frontend
npm install
npm run dev
```

Backend: http://localhost:8082  
Frontend: http://localhost:3002

---

## Tests

```bash
cd project-2-workflow/backend
mvn test
```

32 tests — 32 passing.

Covers: two-level RBAC enforcement, task state machine transitions, WebSocket broadcast on status change, workspace membership scoping, invalid transition rejection, member removal.
