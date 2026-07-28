# Cloud Resource Management Platform

A multi-tenant resource provisioning platform with enforced state machine transitions, append-only audit trail, and role-scoped API access — demonstrating the access control and auditability patterns used in real cloud management systems.

---

## Problem & Solution

This project is a portfolio piece built to practise the backend patterns that underpin real cloud management systems — specifically: enforcing resource lifecycle rules, scoping access by role, and maintaining an immutable record of every action. It simulates a provisioning layer where resources move through defined states, every operation is attributed to a user, and access is restricted by role at the service layer rather than just the API boundary.

---

## Architecture

```
React UI (port 3001)
        │
        ▼
  JwtAuthFilter  ──── validates token, extracts username + role
        │
        ▼
ResourceController  ──── enforces role-based access per endpoint
        │
        ▼
ResourceService  ──── drives state machine, calls AWS SDK
        │
   ┌────┴────┐
   ▼         ▼
ResourceRepo  AuditLogRepo  ──── append-only, every action recorded
        │
        ▼
  PostgreSQL (Docker)
        │
        ▼
AwsProvisioningService  ──── AWS SDK v2 (EC2, S3, CloudWatch)
```

---

## Engineering Highlights

**Resource lifecycle enforcement**
Resources have four statuses: `PENDING`, `RUNNING`, `STOPPED`, `TERMINATED`. `PENDING` is a transient internal state — a resource is saved as `PENDING` first, then immediately moved to `RUNNING` if AWS provisioning succeeds. If provisioning fails, it stays `PENDING` with no recovery path (a known limitation). The enforced rules are: only a `RUNNING` resource can be stopped; a `TERMINATED` resource cannot be terminated again. Violations throw `InvalidStateTransitionException`.

**Append-only audit log**
Every create, stop, terminate, and status update writes a new row to `audit_log`. No updates or deletes — the table is a tamper-evident history of all actions with actor, timestamp, and transition detail.

**Role-scoped access**
Three roles enforced at the service layer, not just the controller:
- `VIEWER` — read-only
- `OPERATOR` — create, stop
- `ADMIN` — full access including terminate

**AWS SDK integration**
`AwsProvisioningService` isolates all AWS SDK v2 calls (EC2, S3). The service layer never calls AWS directly, keeping business logic fully testable without SDK mocks in every test.

**JWT authentication**
Stateless auth via signed JWTs. Token carries username and role — no session state, no DB lookup on every request.

---

## Design Decisions & Trade-offs

**State machine in service layer, not DB constraints** — Transition rules live in code so they can be tested in isolation and extended without schema changes. The trade-off is that direct DB writes bypass the guard, which is acceptable since the DB is not exposed externally.

**Audit log as a separate table** — Keeping audit separate from the resource table means audit history survives resource deletion and can be queried independently. The cost is an extra write on every operation, which is acceptable given the low write volume.

**Role embedded in JWT** — Avoids a DB lookup on every request. The trade-off is that role changes don't take effect until the token expires. Acceptable for this use case; a production system would use short-lived tokens or a token revocation list.

---

## API Reference

| Method | Endpoint                          | Role Required       | Description                  |
|--------|-----------------------------------|---------------------|------------------------------|
| POST   | `/api/auth/register`              | Public              | Register a new user          |
| POST   | `/api/auth/login`                 | Public              | Authenticate, returns JWT    |
| POST   | `/api/resources`                  | OPERATOR, ADMIN     | Provision a new resource     |
| GET    | `/api/resources`                  | All roles           | List all resources           |
| GET    | `/api/resources/{id}`             | All roles           | Get resource by ID           |
| PATCH  | `/api/resources/{id}/stop`        | OPERATOR, ADMIN     | Stop a running resource      |
| DELETE | `/api/resources/{id}`             | OPERATOR, ADMIN     | Terminate a resource         |
| PATCH  | `/api/resources/{id}/status`      | OPERATOR, ADMIN     | Update resource status       |
| GET    | `/api/audit-logs`                 | ADMIN               | View full audit trail        |

**Example — provision a resource:**
```bash
curl -X POST http://localhost:8081/api/resources \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "prod-server", "type": "EC2", "region": "us-east-1"}'
```

**Response:**
```json
{
  "id": "3f7a1c2d-...",
  "name": "prod-server",
  "type": "EC2",
  "region": "us-east-1",
  "status": "RUNNING",
  "createdBy": "operator1",
  "awsResourceId": "i-0abc123def456"  // null if provisioning failed or type is unsupported
}
```

---

## Stack

| Layer    | Technology                                      |
|----------|-------------------------------------------------|
| Backend  | Java 17, Spring Boot 3.2, Spring Security       |
| Auth     | JWT (jjwt 0.11.5)                               |
| Database | PostgreSQL 15 (Docker)                          |
| Cloud    | AWS SDK v2 (EC2, S3)                            |
| Frontend | React, Vite                                     |
| Testing  | JUnit 5, Mockito, Testcontainers, Instancio     |

---

## Running Locally

**Prerequisites:** Java 17, Maven 3.9+, Docker, Node.js

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Start backend
cd project-1-cloud-resource/backend
mvn spring-boot:run

# 3. Start frontend (separate terminal)
cd project-1-cloud-resource/frontend
npm install
npm run dev
```

Backend: http://localhost:8081  
Frontend: http://localhost:3001

---

## Tests

```bash
cd project-1-cloud-resource/backend
mvn test
```

25 tests — 25 passing.

Covers: role-based access enforcement, state machine transitions, audit log writes, AWS provisioning failure handling, resource not found handling.
