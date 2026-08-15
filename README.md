# Polaris
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

> Built Polaris, a lightweight container control plane inspired by AWS ECS/Kubernetes, enabling multi-tenant Docker instance management with quota admission control, desired/current state modeling, asynchronous task execution, reconciliation, audit logging, outbox events, idempotency, and Prometheus-based observability.
