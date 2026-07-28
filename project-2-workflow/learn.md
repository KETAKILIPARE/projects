# Project 2 — Collaborative Workflow Management System: In-Depth Guide

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Security | Spring Security, JWT (jjwt 0.11.5) |
| Database | PostgreSQL 15 (Docker) |
| ORM | Spring Data JPA / Hibernate |
| Realtime | Spring WebSocket, STOMP, SockJS |
| Frontend | React, Vite |
| Testing | JUnit 5, Mockito, H2 (integration tests), Instancio |
| Build | Maven 3.9 |

---

## What Is This Project?

This is a full-stack task management application — similar in concept to Jira or Trello — where teams can create workspaces, invite members, and manage tasks on a Kanban-style board. When a task changes status, every browser connected to that workspace receives the update in real time via WebSockets, without needing to refresh the page.

It is a portfolio project built to practise two-level role-based access control, WebSocket broadcasting, and task state management in a multi-tenant system.

The backend is a Spring Boot REST API with WebSocket support. The frontend is a React app. Data is stored in PostgreSQL.

---

## What Does It Actually Do?

1. Users register and log in. They get a JWT token containing their system-level role.
2. A `SYSTEM_ADMIN` can create workspaces. A `SYSTEM_MEMBER` cannot.
3. The workspace creator is automatically added as a `WORKSPACE_ADMIN`.
4. A `WORKSPACE_ADMIN` can invite other registered users to the workspace, assigning them either `ADMIN` or `MEMBER` role within that workspace.
5. Any workspace member can create tasks and move them between statuses.
6. Only a `WORKSPACE_ADMIN` can delete tasks or remove members.
7. When a task's status changes, the server broadcasts a notification to all WebSocket clients subscribed to that workspace's topic.
8. Members only see workspaces they belong to — not all workspaces in the system.

---

## The Two-Level RBAC

This is the most important design aspect of the project. There are two completely separate role systems.

### System Level (stored in the JWT)
Controls what a user can do across the whole application:
- `SYSTEM_ADMIN` — can create workspaces
- `SYSTEM_MEMBER` — can be invited to workspaces, but cannot create them

This role is stored on the `User` entity and embedded in the JWT at login. The frontend reads it from the token to decide whether to show the "Create Workspace" button.

### Workspace Level (stored in the database)
Controls what a user can do within a specific workspace. Stored in the `workspace_members` table:
- `ADMIN` — can invite members, remove members, delete tasks
- `MEMBER` — can create tasks, move tasks between statuses

This is checked server-side on every request. The `WorkspaceController` has two helper methods:
- `requireMember(workspaceId, username)` — checks the user is in the workspace at all. Throws `NotWorkspaceMemberException` if not.
- `requireRole(workspaceId, username, WorkspaceRole.ADMIN)` — checks the user has ADMIN role in that workspace. Throws `InsufficientRoleException` if not.

These are called at the top of every controller method that needs them. The workspace role is never in the JWT — it is always looked up from the database because it can change (a member can be promoted or removed).

---

## How Tasks Work

### Creating a Task
Any workspace member can create a task. The task starts with status `TODO`. It has a title, optional description, optional assignee (must be a workspace member), and records who created it.

### Moving a Task
Any workspace member can update a task's status. The `TaskStateTransitionValidator` is called before the update is saved. Looking at the actual code:

```java
public static boolean isValid(TaskStatus from, TaskStatus to) {
    return from != to;
}
```

This means any transition is valid as long as the new status is different from the current one. You can move a task from `TODO` directly to `DONE`, or from `DONE` back to `TODO`. There is no enforced ordering — the validator just prevents a no-op update (setting a task to the status it already has).

The four statuses are: `TODO`, `IN_PROGRESS`, `REVIEW`, `DONE`.

### After a Status Update
After the task is saved, `NotificationService.broadcastTaskUpdate()` is called. This sends a `TaskNotification` object (containing task ID, title, new status, workspace ID, and who made the change) to the STOMP topic `/topic/workspace/{workspaceId}`. Every WebSocket client subscribed to that topic receives it immediately.

---

## How WebSockets Work

The WebSocket setup uses Spring's STOMP support.

`WebSocketConfig` registers:
- A simple in-memory message broker on the `/topic` prefix
- The WebSocket endpoint at `/ws` with SockJS fallback (SockJS allows the connection to fall back to HTTP long-polling if WebSockets are not available in the browser)
- Application destination prefix `/app` (for client-to-server messages, not used here)

The frontend connects to `ws://localhost:8082/ws` using SockJS + STOMP client, then subscribes to `/topic/workspace/{workspaceId}`.

When `NotificationService` calls `messagingTemplate.convertAndSend("/topic/workspace/" + workspaceId, notification)`, Spring's in-memory broker delivers it to all subscribers of that topic.

This is a broadcast model — everyone in the workspace gets the notification. There is no per-user filtering at the WebSocket level.

---

## How Authentication Works

Authentication works the same way as Project 1 — JWT-based, stateless.

Registration: POST `/api/auth/register` with username, password, and system role. Password is BCrypt-hashed.

Login: POST `/api/auth/login`. Returns a JWT containing the username and system role (`SYSTEM_ADMIN` or `SYSTEM_MEMBER`).

Every request: `JwtAuthFilter` reads the `Authorization` header, validates the token, loads the user from the database, and sets the authentication on `SecurityContextHolder`.

The workspace-level role is NOT in the JWT. It is checked from the `workspace_members` table on every request that needs it.

---

## The Database

PostgreSQL runs in Docker. The main tables are:

**users**
```
id            UUID
username      VARCHAR (unique)
password      VARCHAR (BCrypt hash)
system_role   VARCHAR (SYSTEM_ADMIN, SYSTEM_MEMBER)
```

**workspaces**
```
id            UUID
name          VARCHAR
created_by    VARCHAR
```

**workspace_members**
```
id            UUID
workspace_id  UUID (references workspaces)
username      VARCHAR
role          VARCHAR (ADMIN, MEMBER)
```

**tasks**
```
id            UUID
title         VARCHAR
description   VARCHAR (nullable)
status        VARCHAR (TODO, IN_PROGRESS, REVIEW, DONE)
workspace_id  UUID
assignee      VARCHAR (nullable)
created_by    VARCHAR
created_at    TIMESTAMP
updated_at    TIMESTAMP
```

---

## How the Code Is Organised

```
com.workflow
├── config/
│   ├── SecurityConfig.java     — Spring Security + JwtAuthFilter
│   └── WebSocketConfig.java    — STOMP broker + /ws endpoint
├── controller/
│   ├── AuthController.java     — register, login
│   ├── WorkspaceController.java — workspace CRUD + member management + task operations
│   ├── TaskController.java     — standalone task endpoints (also accessible via workspace routes)
│   └── UserController.java     — list all users (for invite dropdown in UI)
├── domain/
│   ├── Task.java               — JPA entity
│   ├── TaskStatus.java         — enum: TODO, IN_PROGRESS, REVIEW, DONE
│   ├── Workspace.java          — JPA entity
│   ├── WorkspaceMember.java    — JPA entity (workspace_id + username + role)
│   ├── WorkspaceRole.java      — enum: ADMIN, MEMBER
│   ├── User.java               — JPA entity
│   └── SystemRole.java         — enum: SYSTEM_ADMIN, SYSTEM_MEMBER
├── dto/
│   ├── TaskRequest/Response    — task create and read DTOs
│   ├── TaskStatusUpdateRequest — just the new status
│   ├── TaskNotification        — what gets broadcast over WebSocket
│   ├── AddMemberRequest        — username + role
│   ├── MemberResponse          — username + role
│   └── Login/Register DTOs
├── exception/
│   ├── InsufficientRoleException
│   ├── InvalidTaskTransitionException
│   ├── NotWorkspaceMemberException
│   ├── TaskNotFoundException
│   ├── WorkspaceNotFoundException
│   └── GlobalExceptionHandler  — maps all exceptions to HTTP responses
├── repository/
│   ├── TaskRepository
│   ├── WorkspaceRepository     — includes findByMemberUsername (custom JPQL query)
│   ├── WorkspaceMemberRepository — includes deleteByWorkspaceIdAndUsername
│   └── UserRepository
├── service/
│   ├── TaskService             — create, update status, delete, find
│   ├── NotificationService     — broadcasts TaskNotification via STOMP
│   └── UserDetailsServiceImpl  — loads user for Spring Security
└── util/
    ├── JwtUtil                 — generate and validate JWTs
    └── TaskStateTransitionValidator — checks from != to
```

---

## A Note on Member Removal

Removing a workspace member uses a JPQL delete query in `WorkspaceMemberRepository`:

```java
@Modifying
@Transactional
void deleteByWorkspaceIdAndUsername(UUID workspaceId, String username);
```

This is a direct delete query — it does not load the entity first. Spring Data JPA requires `@Modifying` on any query that writes data (insert, update, delete) and `@Transactional` to ensure the operation runs in a transaction. Without these annotations, the delete would either fail or not execute. The `@Transactional` on the controller method calling this also ensures the whole operation is atomic.

---

## How to Run It

### Prerequisites
- Java 17
- Maven 3.9+
- Docker (for PostgreSQL)
- Node.js (for the frontend)

### Step 1 — Start PostgreSQL

From the root `bubu` folder:

```bash
docker compose up -d
```

### Step 2 — Start the Backend

```bash
cd project-2-workflow/backend
mvn spring-boot:run
```

Backend starts on http://localhost:8082

On Windows without Maven on PATH:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-17'
& 'C:\path\to\maven\apache-maven-3.9.6\bin\mvn.cmd' spring-boot:run
```

### Step 3 — Start the Frontend

```bash
cd project-2-workflow/frontend
npm install
npm run dev
```

Frontend starts on http://localhost:3002

### Step 4 — Try It

Register a SYSTEM_ADMIN:
```bash
curl -X POST http://localhost:8082/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "password123", "systemRole": "SYSTEM_ADMIN"}'
```

Login:
```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "password123"}'
```

Create a workspace (SYSTEM_ADMIN only):
```bash
curl -X POST http://localhost:8082/api/workspaces \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "Team Alpha"}'
```

Invite a member:
```bash
curl -X POST http://localhost:8082/api/workspaces/{workspaceId}/members \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"username": "bob", "role": "MEMBER"}'
```

Create a task:
```bash
curl -X POST http://localhost:8082/api/workspaces/{workspaceId}/tasks \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"title": "Build login page", "description": "React form with validation"}'
```

Move a task:
```bash
curl -X PATCH http://localhost:8082/api/workspaces/{workspaceId}/tasks/{taskId}/status \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"status": "IN_PROGRESS"}'
```

### Running the Tests

```bash
cd project-2-workflow/backend
mvn test
```

32 tests — 32 passing. 18 are integration tests using H2 in-memory database. 14 are unit tests using Mockito.
