# Project 1 — Cloud Resource Management Platform: In-Depth Guide

---

## What Is This Project?

This is a full-stack web application that lets users provision and manage cloud resources (EC2 instances and S3 buckets) through a web UI and REST API, instead of going directly into the AWS console.

It is a portfolio project built to practise backend patterns that appear in real cloud management systems: role-based access control, resource lifecycle state machines, immutable audit logging, and AWS SDK integration.

The backend is a Spring Boot REST API. The frontend is a React app. Data is stored in PostgreSQL. When you create an EC2 or S3 resource, the backend actually calls the AWS SDK and provisions a real resource on AWS.

---

## What Does It Actually Do?

1. A user registers and logs in. They get back a JWT token.
2. They use that token on every subsequent request.
3. Depending on their role (ADMIN, OPERATOR, or VIEWER), they can create, stop, or terminate resources.
4. When a resource is created, the backend saves it to the database, then calls the AWS SDK to provision it on AWS (EC2 or S3).
5. Every action — create, stop, terminate, status update — is written to an audit log table. That table is append-only. Nothing is ever updated or deleted from it.
6. The frontend shows a dashboard of all resources with their current status.

---

## The Three Roles

There are three roles in the system. They are stored on the user record and embedded in the JWT at login.

- **VIEWER** — can only read. Cannot create, stop, or terminate anything.
- **OPERATOR** — can create resources and stop them. Cannot terminate.
- **ADMIN** — full access. Can do everything including terminate.

Role checks happen in two places:
1. `@PreAuthorize` annotations on the controller methods (Spring Security checks the JWT role before the method runs).
2. Inside the service methods themselves — the service receives the role and throws `AccessDeniedException` if the caller is not allowed.

The double-check is intentional. The controller check is the first line of defence. The service check means the business logic is self-protecting even if the controller annotation is ever removed or bypassed.

---

## The Resource State Machine

Every resource has a status. The valid statuses are:

```
PENDING → RUNNING → STOPPED → RUNNING (can restart)
                 ↘
                  TERMINATED (final, cannot leave)
```

When you create a resource, it starts as `PENDING`. If AWS provisioning succeeds, it moves to `RUNNING`. If provisioning fails, it stays in whatever state it was.

The transitions are enforced in `ResourceService`:
- You can only stop a `RUNNING` resource. Trying to stop a `PENDING` or `STOPPED` resource throws `InvalidStateTransitionException`.
- You can terminate a resource from any state except `TERMINATED`. Trying to terminate an already-terminated resource throws `InvalidStateTransitionException`.

These rules live in code, not in database constraints. That means they are testable in isolation and can be changed without a schema migration.

---

## The Audit Log

Every time a resource is created, stopped, terminated, or has its status updated, a new row is written to the `audit_logs` table. The row contains:

- `resourceId` — which resource was acted on
- `performedBy` — the username of who did it
- `action` — a string describing what happened (e.g. `CREATED:AWS_ID=i-0abc123`, `STOPPED`, `TERMINATED`)
- `performedAt` — the timestamp

The `AuditLog` entity has no setters. Once created, it cannot be modified. There is no update or delete operation on this table anywhere in the codebase. This makes it a tamper-evident record of everything that happened.

---

## How Authentication Works

### Registration
You POST to `/api/auth/register` with a username, password, and optional role. The password is hashed with BCrypt before being saved. If the username already exists, you get a 409 Conflict.

### Login
You POST to `/api/auth/login` with username and password. Spring's `AuthenticationManager` verifies the credentials against the database. If valid, `JwtUtil` generates a signed JWT containing the username and role. That token is returned to the client.

### Every Other Request
The `JwtAuthFilter` (inside `SecurityConfig`) runs on every request. It:
1. Reads the `Authorization: Bearer <token>` header.
2. Calls `JwtUtil.isValid(token)` — this parses the JWT and checks the signature and expiry.
3. If valid, extracts the username, loads the full `UserDetails` from the database, and sets the authentication on Spring's `SecurityContextHolder`.
4. The request then proceeds to the controller with the user's identity and roles attached.

If the token is missing, invalid, or expired, the filter does nothing — the request proceeds unauthenticated, and Spring Security rejects it with a 401.

The JWT is signed with an HMAC-SHA key derived from a secret in `application.properties`. The secret and expiry time are injected via `@Value`.

---

## How AWS Provisioning Works

`AwsProvisioningService` is the only class that talks to AWS. It holds an `Ec2Client` and `S3Client` injected by Spring (configured in `AwsConfig`).

**EC2:**
When you provision an EC2 resource, the service looks up the correct Amazon Linux 2023 AMI for the requested region from a hardcoded map, then calls `RunInstancesRequest` to launch a `t2.micro` instance. The instance is tagged with the resource name and `ManagedBy=CloudOps`. The returned instance ID is stored as `awsResourceId` on the resource record.

Stop, start, and terminate call the corresponding EC2 SDK methods using that stored instance ID.

**S3:**
When you provision an S3 resource, the service generates a unique bucket name (`cloudops-<name>-<timestamp>`), creates the bucket, then immediately applies a public access block configuration — all four block settings are set to true. The bucket name is stored as `awsResourceId`.

Terminating an S3 resource first lists and deletes all objects in the bucket, then deletes the bucket itself.

**Important:** If AWS credentials are not configured in the environment, all AWS calls will fail. The service catches the exception, logs it, and records the failure in the audit log. The resource stays in `PENDING` state rather than crashing the whole request.

---

## The Database

PostgreSQL runs in Docker. There are two main tables:

**resources**
```
id           UUID (primary key, auto-generated)
name         VARCHAR
type         VARCHAR (EC2, S3)
region       VARCHAR
status       VARCHAR (PENDING, RUNNING, STOPPED, TERMINATED)
created_by   VARCHAR
created_at   TIMESTAMP
updated_at   TIMESTAMP
aws_resource_id VARCHAR (nullable — null if provisioning failed)
```

**audit_logs**
```
id            UUID (primary key)
resource_id   UUID (references resources)
performed_by  VARCHAR
action        VARCHAR
performed_at  TIMESTAMP
```

---

## How the Code Is Organised

```
com.cloudresource
├── config/
│   ├── AwsConfig.java          — creates Ec2Client and S3Client beans
│   └── SecurityConfig.java     — Spring Security config + JwtAuthFilter (inner class)
├── controller/
│   ├── AuthController.java     — /api/auth/register and /api/auth/login
│   ├── ResourceController.java — /api/resources CRUD + stop/terminate
│   └── AuditLogController.java — /api/audit-logs (ADMIN only)
├── domain/
│   ├── Resource.java           — JPA entity for the resources table
│   ├── AuditLog.java           — JPA entity for audit_logs (no setters)
│   ├── User.java               — JPA entity for users
│   ├── ResourceStatus.java     — enum: PENDING, RUNNING, STOPPED, TERMINATED
│   ├── ResourceType.java       — enum: EC2, S3
│   └── UserRole.java           — enum: ADMIN, OPERATOR, VIEWER
├── dto/
│   ├── ResourceRequest.java    — name, type, region
│   ├── ResourceResponse.java   — what the API returns
│   ├── LoginRequest/Response   — auth DTOs
│   └── ...
├── exception/
│   ├── AccessDeniedException.java
│   ├── InvalidStateTransitionException.java
│   ├── ResourceNotFoundException.java
│   └── GlobalExceptionHandler.java — maps exceptions to HTTP status codes
├── repository/
│   ├── ResourceRepository.java
│   ├── AuditLogRepository.java
│   └── UserRepository.java
├── service/
│   ├── ResourceService.java        — all business logic, state machine, audit writes
│   ├── AwsProvisioningService.java — AWS SDK calls only
│   └── UserDetailsServiceImpl.java — loads user from DB for Spring Security
└── util/
    └── JwtUtil.java — generate and validate JWTs
```

---

## How to Run It

### Prerequisites
- Java 17
- Maven 3.9+
- Docker (for PostgreSQL)
- Node.js (for the frontend)
- AWS credentials configured (optional — the app runs without them, AWS calls will just fail gracefully)

### Step 1 — Start PostgreSQL

From the root `bubu` folder:

```bash
docker compose up -d
```

This starts a PostgreSQL container on port 5432. The database, username, and password are configured in `docker-compose.yml`.

### Step 2 — Start the Backend

```bash
cd project-1-cloud-resource/backend
mvn spring-boot:run
```

The backend starts on http://localhost:8081

If you are on Windows and Maven is not on your PATH:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Zulu\zulu-17'
& 'C:\path\to\maven\apache-maven-3.9.6\bin\mvn.cmd' spring-boot:run
```

### Step 3 — Start the Frontend

In a separate terminal:

```bash
cd project-1-cloud-resource/frontend
npm install
npm run dev
```

The frontend starts on http://localhost:3001

### Step 4 — Try It

Register a user:
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "password123", "role": "ADMIN"}'
```

Login:
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "password123"}'
```

Copy the token from the response, then create a resource:
```bash
curl -X POST http://localhost:8081/api/resources \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "my-server", "type": "EC2", "region": "us-east-1"}'
```

### Running the Tests

```bash
cd project-1-cloud-resource/backend
mvn test
```

25 tests — 25 passing. Tests use Mockito to mock the repositories and AWS service, and Instancio to generate test data.

---

## What the Frontend Does

The React frontend (Vite) has:
- A register page and login page
- A dashboard that lists all resources with their name, type, region, status, and who created them
- A status dropdown on each resource card to change its status
- The JWT is stored in memory and sent as a Bearer token on every API call

CORS is configured in `SecurityConfig` to allow requests from `http://localhost:3001` only.
