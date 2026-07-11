# AGENTS.md

This is the project instruction file for Polaris.

## Project Context

Polaris is a backend project that simulates a small cloud control plane / container orchestrator.

Core concepts:

- Tenant
- Instance
- Tenant quota
- Desired state vs current state
- Instance state machine
- Reconciliation loop
- Outbox events
- Audit logs
- Idempotency
- Authorization

Tech stack:

- Java 21
- Spring Boot
- PostgreSQL
- Flyway
- JPA/Hibernate
- Spring Security/JWT
- Docker
- Gradle Kotlin DSL
- JUnit 5
- Mockito
- Testcontainers
- MockMvc

## Skill Routing

Use the `swe-senior-mentor` skill when the task is about:

- implementing features
- designing APIs
- designing entities/tables
- writing services
- refactoring code
- debugging backend logic
- reviewing architecture
- improving code quality
- explaining engineering decisions

Use the `qa-testing-senior` skill when the task is about:

- writing tests
- designing test cases
- reviewing test code
- creating test strategy
- API testing
- integration testing
- security testing
- regression testing
- Testcontainers
- MockMvc
- Mockito

If the task includes both implementation and testing:

1. Use `swe-senior-mentor` first for design and implementation.
2. Then use `qa-testing-senior` for test strategy and test code.

## General Rules

Before modifying code:

1. Understand the requirement.
2. Identify affected files.
3. Explain the plan briefly.
4. Make focused changes.
5. Avoid unrelated refactors.

For backend code:

- Keep controllers thin.
- Put business logic in services/domain layer.
- Use DTOs for API request/response.
- Use centralized exception handling.
- Use transactions for state-changing operations.
- Use database constraints for critical invariants.
- Use optimistic locking for concurrent quota/state updates.
- Do not expose JPA entities directly in API responses.
- Do not add unnecessary infrastructure.

For tests:

- Analyze risks before writing tests.
- Prioritize business rules, state transitions, validation, security, database constraints, transactions, concurrency, and idempotency.
- Use Mockito for isolated service logic.
- Use MockMvc for controller/API tests.
- Use Testcontainers for PostgreSQL integration tests.
- Avoid meaningless getter/setter tests.
- Avoid over-mocked tests.