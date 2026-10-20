## Highlights

- Multi-tenant instance management
- Tenant quota admission control
- Desired state / current state lifecycle model
- Durable resource reconciliation queue in PostgreSQL
- Docker compute provider abstraction
- State machine for instance lifecycle safety
- Reconciliation loop for drift detection and repair
- Unified CREATE/START/STOP/DELETE reconciliation and idempotent desired-state updates
- Actuator health endpoint and Micrometer/Prometheus dependencies

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

> Polaris is a lightweight Docker container control plane with tenant quota admission, asynchronous provisioning, and declarative lifecycle reconciliation. Authentication currently uses sessions and CSRF; tenant authorization, audit logging, transactional outbox, and custom reconciliation metrics remain unfinished.

## Tài liệu implementation

Bắt đầu từ [mục lục tính năng](D:/Workspace/polaris/docs/features/README.md), đối chiếu source ngày 20/10/2026. Mỗi tài liệu giải thích mục đích, công nghệ, luồng hoạt động, transaction/concurrency, cách kiểm chứng và giới hạn hiện tại.

- [Tenant và quota](D:/Workspace/polaris/docs/features/tenant-and-quota.md)
- [Instance API](D:/Workspace/polaris/docs/features/instance-api.md)
- [Task engine đã loại bỏ: ghi chú chuyển đổi](D:/Workspace/polaris/docs/features/provisioning-task-engine.md)
- [Declarative reconciliation V3](D:/Workspace/polaris/docs/features/declarative-reconciliation-v3.md)
- [Failure cleanup và quota release](D:/Workspace/polaris/docs/features/failure-cleanup-and-quota-release.md)
- [Docker compute provider](D:/Workspace/polaris/docs/features/docker-compute-provider.md)
- [Authentication và IAM](D:/Workspace/polaris/docs/features/authentication-and-iam.md)
- [Database schema thực tế](D:/Workspace/polaris/docs/features/database-schema.md)
- [Chạy local, kiểm chứng và observability](D:/Workspace/polaris/docs/features/local-runtime-and-observability.md)
- [Postman collection và cách chạy](D:/Workspace/polaris/docs/postman/v1/README.md)

Main source/resources của flow không còn task đã build thành công. Báo cáo Postman pass trước đây thuộc flow trước khi chuyển CREATE vào reconcile; runtime mới và automated test suite chưa được xác nhận, test cũ còn tham chiếu task đã xóa. Các tài liệu design cũ mô tả cả roadmap, không phải danh sách tính năng đã triển khai. Thư mục docs/features hiện bị Git ignore; các link tài liệu mới chỉ có đủ đích trong workspace local cho đến khi được đưa vào Git.
