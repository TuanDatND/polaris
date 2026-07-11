# Polaris

**Polaris** is a lightweight container control plane for managing Docker-based compute instances through a cloud-style API.

It is designed as a backend/system engineering project inspired by AWS ECS and Kubernetes control plane ideas: users declare the desired state of an instance, while background workers and reconcilers make the real Docker state converge toward that intent.

```text
User intent      -> desired_state
System reality   -> current_state
Execution         -> async task worker
Safety net        -> reconciliation loop
Provider v1       -> Docker
Source of truth   -> PostgreSQL
```

## Highlights

- Multi-tenant instance management
- Tenant quota admission control
- Desired state / current state lifecycle model
- Async DB-backed task engine
- Docker compute provider abstraction
- State machine for instance lifecycle safety
- Reconciliation loop for drift detection and repair
- Audit log and outbox event pattern
- Idempotency for safe client retries
- Metrics and health checks with Actuator, Micrometer and Prometheus

## Tech Stack

- Java 21
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- PostgreSQL
- Flyway
- Docker Java SDK
- Spring Boot Actuator
- Micrometer + Prometheus
- Springdoc OpenAPI
- JUnit 5 + Testcontainers
- Gradle Kotlin DSL

## Core Idea

Polaris is not a normal CRUD application.

When a user creates an instance, the API does not create a Docker container directly inside the HTTP request. Instead, Polaris:

1. Validates the request.
2. Checks and reserves tenant quota.
3. Stores the instance intent in PostgreSQL.
4. Enqueues an async task.
5. Returns quickly to the client.
6. Lets a worker provision the Docker container in the background.
7. Lets a reconciler repair drift between database state and real Docker state.

## Main Modules

```text
tenant       # Tenant, quota, admission control
instance     # Instance lifecycle and state machine
task         # Async task queue and workers
provider     # Compute provider abstraction and Docker implementation
reconcile    # Drift detection and repair
audit        # Append-only audit log
outbox       # Durable domain events
common       # API response, exceptions, tenant context
```

## Local Development

Requirements:

- JDK 21
- Docker
- PostgreSQL 15+

Start PostgreSQL:

```bash
docker run --name polaris-postgres \
  -e POSTGRES_DB=polaris \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=12345 \
  -p 5432:5432 \
  -d postgres:15
```

Run the application:

```bash
./gradlew bootRun
```

On Windows:

```powershell
.\gradlew.bat bootRun
```

Useful URLs:

```text
App          http://localhost:8080
Swagger UI   http://localhost:8080/swagger-ui/index.html
Health       http://localhost:8080/actuator/health
Prometheus   http://localhost:8080/actuator/prometheus
```

## Documentation

- [AI project context](docs/AI_PROJECT_CONTEXT.md): detailed architecture and product context intended for AI assistants or deep project onboarding.
- [.learn](.learn): design notes, architecture guide and database schema references.

## Project Positioning

Polaris demonstrates backend/system design beyond basic REST CRUD:

- separating user intent from system reality
- avoiding external side effects inside HTTP transactions
- using a database-backed queue for reliable async execution
- enforcing lifecycle correctness with a state machine
- protecting tenant resources with quota admission control
- recovering from drift through reconciliation
- preserving domain events with the outbox pattern

Short CV description:

> Built Polaris, a lightweight container control plane inspired by AWS ECS/Kubernetes, enabling multi-tenant Docker instance management with quota admission control, desired/current state modeling, asynchronous task execution, reconciliation, audit logging, outbox events, idempotency, and Prometheus-based observability.
