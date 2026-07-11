# Polaris

**Polaris** là một **Lightweight Container Control Plane** dùng để quản lý vòng đời Docker container thông qua API, lấy cảm hứng từ các hệ thống như AWS ECS, Kubernetes control plane và các cloud platform thật.

Thay vì xử lý container trực tiếp trong HTTP request như một CRUD app thông thường, Polaris hoạt động theo mô hình control plane:

```text
User intent      -> desired_state
System reality   -> current_state
Execution         -> async task worker
Safety net        -> reconciliation loop
Provider v1       -> Docker
Source of truth   -> PostgreSQL
```

Người dùng gọi API để tạo, start, stop hoặc delete instance. Polaris ghi nhận mong muốn đó vào database, sau đó worker và reconciler phía sau sẽ biến mong muốn thành trạng thái thật trên Docker.

---

## Mục tiêu dự án

Polaris được thiết kế như một dự án backend/system engineering hoàn chỉnh, tập trung vào các vấn đề thường gặp trong hệ thống cloud thật:

- Multi-tenant resource management
- Quota admission control
- Desired state / current state model
- Async task execution
- Retry và failure handling
- State machine cho vòng đời instance
- Docker compute provider abstraction
- Reconciliation để phát hiện và sửa state drift
- Audit log append-only
- Outbox pattern cho domain event
- Idempotency để chống tạo trùng request
- Metrics, health check và observability

Tóm gọn:

> Polaris cho phép nhiều tenant tạo và quản lý Docker container như “cloud instances”, có quota, async worker, state machine, reconciliation loop, audit log, idempotency và observability.

---

## Kiến trúc tổng quan

```text
Client / Tenant
      |
      | HTTP API
      v
+-------------------+
|   API Layer       |
| validate request  |
| check quota       |
| write intent      |
+-------------------+
      |
      | transaction
      v
+-------------------+
|   PostgreSQL      |
| tenants           |
| instances         |
| tasks             |
| outbox_events     |
| audit_logs        |
+-------------------+
      ^
      |
      | poll task / reconcile state
      |
+-------------------+        +-------------------+
|   Task Worker     | -----> | Docker Provider   |
| execute task      |        | create/start/stop |
| retry on failure  |        | delete/inspect    |
+-------------------+        +-------------------+
      ^
      |
+-------------------+
|   Reconciler      |
| detect drift      |
| repair state      |
+-------------------+
```

Nguyên tắc quan trọng:

- API thread phải nhanh: validate, check quota, ghi intent, enqueue task, trả response.
- API không gọi Docker trực tiếp.
- Database là source of truth.
- Worker xử lý side effect với Docker.
- Reconciler đảm bảo trạng thái thật dần quay về trạng thái mong muốn.

---

## Tech Stack

- **Java 21**
- **Spring Boot 3.5**
- **Spring Web**
- **Spring Data JPA**
- **PostgreSQL**
- **Flyway Migration**
- **Docker Java SDK**
- **Spring Boot Actuator**
- **Micrometer + Prometheus**
- **Springdoc OpenAPI / Swagger UI**
- **JUnit 5**
- **Testcontainers**
- **Gradle Kotlin DSL**

---

## Domain chính

### Tenant

Tenant đại diện cho khách hàng hoặc namespace sử dụng Polaris.

Mỗi tenant có quota riêng:

```text
quota_cpu
quota_ram_mb
quota_instance_count
```

Và counter tài nguyên đã được reserve:

```text
allocated_cpu
allocated_ram_mb
allocated_instance_count
```

Khi tenant tạo instance, Polaris kiểm tra quota trước. Nếu vượt quota, request bị từ chối bằng lỗi quota thay vì tạo resource nửa vời.

### Instance

Instance là resource chính của Polaris. Một instance tương ứng với một Docker container do Polaris quản lý.

Instance có hai loại state:

```text
desired_state = user muốn gì
current_state = hệ thống đang thật sự ở đâu
```

Ví dụ:

```text
desired_state = RUNNING
current_state = PENDING
```

Nghĩa là user muốn instance chạy, nhưng hệ thống vẫn chưa provision xong container.

### Task

Task là đơn vị công việc async cho worker.

Các task chính:

```text
CREATE_INSTANCE
START_INSTANCE
STOP_INSTANCE
DELETE_INSTANCE
RECONCILE_INSTANCE
```

Task có trạng thái:

```text
QUEUED
RUNNING
SUCCESS
FAILED
CANCELLED
```

Task hỗ trợ retry thông qua `attempts`, `max_attempts`, `available_at` và `last_error`.

---

## State Machine

Polaris kiểm soát vòng đời instance bằng state machine để tránh chuyển state sai.

Các `desired_state`:

```text
RUNNING
STOPPED
DELETED
```

Các `current_state`:

```text
PENDING
PROVISIONING
STARTING
RUNNING
STOPPING
STOPPED
DELETING
DELETED
FAILED
```

Transition hợp lệ:

```text
PENDING      -> PROVISIONING
PROVISIONING -> RUNNING
PROVISIONING -> FAILED
RUNNING      -> STOPPING
STOPPING     -> STOPPED
STOPPED      -> STARTING
STARTING     -> RUNNING
RUNNING      -> DELETING
STOPPED      -> DELETING
FAILED       -> DELETING
DELETING     -> DELETED
```

Ví dụ transition không hợp lệ:

```text
DELETED -> RUNNING
PENDING -> STOPPED
RUNNING -> PROVISIONING
```

Những transition sai sẽ bị chặn bằng domain exception.

---

## Flow tạo instance

Client gửi request:

```http
POST /api/v1/instances
X-Tenant-Id: <tenant-id>
Idempotency-Key: <optional-key>
Content-Type: application/json
```

```json
{
  "name": "my-nginx",
  "imageName": "nginx:latest",
  "cpuAllocated": 1,
  "ramMb": 512
}
```

Polaris xử lý trong API transaction:

```text
1. Xác thực tenant
2. Lock/check quota
3. Reserve quota
4. Insert instance
   desired_state = RUNNING
   current_state = PENDING
5. Insert task CREATE_INSTANCE
6. Insert audit log
7. Insert outbox event
8. Commit
9. Trả 202 Accepted hoặc 201 Created
```

Worker xử lý phía sau:

```text
1. Poll task QUEUED
2. Mark task RUNNING
3. Update current_state = PROVISIONING
4. Gọi Docker provider để create/start container
5. Lưu provider resource/container id
6. Update current_state = RUNNING
7. Mark task SUCCESS
8. Ghi audit log, state history và outbox event
```

Client có thể gọi:

```http
GET /api/v1/instances/{id}
X-Tenant-Id: <tenant-id>
```

Response ví dụ:

```json
{
  "id": "2c88c1e5-1c3d-4ef5-b21f-47e8d5f85b10",
  "tenantId": "b8c8eb19-078a-4ea1-9307-335b41d1d0a9",
  "name": "my-nginx",
  "imageName": "nginx:latest",
  "cpuAllocated": 1,
  "ramMb": 512,
  "desiredState": "RUNNING",
  "currentState": "RUNNING",
  "containerId": "docker-container-id",
  "failureReason": null
}
```

---

## API hoàn chỉnh

### Tenant API

```http
POST  /api/v1/tenants
GET   /api/v1/tenants/{id}
GET   /api/v1/tenants
PATCH /api/v1/tenants/{id}/quota
```

### Instance API

```http
POST   /api/v1/instances
GET    /api/v1/instances
GET    /api/v1/instances/{id}
POST   /api/v1/instances/{id}/start
POST   /api/v1/instances/{id}/stop
DELETE /api/v1/instances/{id}
```

### Task API

```http
GET  /api/v1/tasks
GET  /api/v1/tasks/{id}
POST /api/v1/tasks/{id}/retry
POST /api/v1/tasks/{id}/cancel
```

### Audit API

```http
GET /api/v1/audit-logs
GET /api/v1/instances/{id}/audit-logs
```

### Admin / Operations API

```http
POST /api/v1/admin/reconcile
GET  /api/v1/admin/instances/stuck
GET  /api/v1/admin/tasks/failed
```

### Observability

```http
GET /actuator/health
GET /actuator/prometheus
GET /swagger-ui/index.html
```

---

## Database Schema

Các bảng chính của Polaris bản hoàn chỉnh:

```text
tenants
instances
tasks
provider_resources
instance_state_history
audit_logs
outbox_events
idempotency_keys
```

Quan hệ tổng quan:

```text
tenants
  ├── instances
  │     ├── tasks
  │     ├── provider_resources
  │     └── instance_state_history
  │
  ├── audit_logs
  └── idempotency_keys
```

Thứ tự migration khuyến nghị:

```text
V1__create_tenants.sql
V2__create_instances.sql
V3__create_tasks.sql
V4__create_outbox_events.sql
V5__create_audit_logs.sql
V6__create_idempotency_keys.sql
V7__create_instance_state_history.sql
V8__create_provider_resources.sql
V9__create_updated_at_trigger.sql
```

---

## Module code

```text
com.cloud.polaris
├── tenant       # Tenant, quota, admission control
├── instance     # Instance lifecycle, state machine
├── task         # DB-backed async task engine
├── provider     # ComputeProvider abstraction
├── reconcile    # Drift detection and repair
├── audit        # Append-only audit log
├── outbox       # Durable domain events
├── event        # Domain events
├── common       # API response, exception, tenant context
└── config       # Security, Docker, scheduler, OpenAPI, observability
```

Thiết kế sử dụng **package-by-feature** để mỗi module giữ gần nhau các domain object, service, repository và API liên quan.

---

## Provider abstraction

Polaris không để business service gọi Docker trực tiếp. Tất cả thao tác compute đi qua interface:

```java
public interface ComputeProvider {
    ProviderResource createContainer(CreateContainerRequest request);
    void start(String providerResourceId);
    void stop(String providerResourceId);
    void delete(String providerResourceId);
    ProviderResourceStatus inspect(String providerResourceId);
}
```

Implementation đầu tiên là:

```text
DockerComputeProvider
```

Nhờ đó, logic control plane có thể giữ ổn định nếu sau này thay Docker bằng Kubernetes, ECS hoặc một provider khác.

---

## Reconciliation

Reconciler chạy định kỳ để so sánh:

```text
Database desired/current state
        vs
Actual Docker container state
```

Các case reconciler xử lý:

- `desired_state = RUNNING` nhưng container thật đã stop hoặc biến mất.
- `desired_state = STOPPED` nhưng container vẫn đang chạy.
- Instance kẹt quá lâu ở `PROVISIONING`, `STARTING`, `STOPPING` hoặc `DELETING`.
- Task đang `RUNNING` nhưng worker chết giữa chừng.
- Provider resource tồn tại nhưng database không còn đồng bộ.

Reconciler không thay thế worker. Nó là safety net để hệ thống tự phục hồi khi có drift hoặc failure.

---

## Outbox và Audit

Polaris dùng hai cơ chế khác nhau cho hai mục đích khác nhau:

### Audit Log

Audit log là append-only history cho hành động quan trọng:

```text
CREATE_TENANT
CREATE_INSTANCE
START_INSTANCE
STOP_INSTANCE
DELETE_INSTANCE
TASK_STARTED
TASK_SUCCEEDED
TASK_FAILED
RECONCILE_INSTANCE
QUOTA_RESERVED
QUOTA_RELEASED
```

Audit log giúp trả lời:

- Ai tạo instance?
- Ai stop/delete instance?
- Task nào fail?
- Reconciler đã can thiệp lúc nào?
- Quota được reserve/release khi nào?

### Outbox Pattern

Outbox đảm bảo domain event không bị mất sau khi transaction commit:

```text
1. Update business table
2. Insert outbox_event
3. Commit transaction
4. OutboxRelay publish/process event sau
```

Ở bản đầu, outbox relay có thể log event hoặc tạo audit side effect. Về sau có thể publish sang Kafka, RabbitMQ hoặc webhook.

---

## Idempotency

Các API tạo resource hỗ trợ idempotency key:

```http
Idempotency-Key: create-nginx-001
```

Nếu client timeout rồi retry cùng một request, Polaris trả lại response cũ thay vì tạo instance thứ hai.

Idempotency được scope theo tenant:

```text
(tenant_id, idempotency_key)
```

Điều này mô phỏng cách các cloud/payment API thật xử lý retry an toàn.

---

## Metrics

Polaris expose metrics qua Actuator và Prometheus.

Các metric quan trọng:

```text
polaris_instances_total
polaris_instances_running
polaris_instances_failed
polaris_tasks_queued
polaris_tasks_running
polaris_tasks_failed
polaris_task_duration_seconds
polaris_reconcile_runs_total
polaris_reconcile_drift_detected_total
polaris_docker_calls_failed_total
polaris_tenant_quota_cpu_allocated
polaris_tenant_quota_ram_allocated
```

Log nên có các correlation field:

```text
requestId
tenantId
instanceId
taskId
workerId
```

---

## Chạy local

### Yêu cầu

- JDK 21
- Docker
- PostgreSQL 15+
- Gradle Wrapper có sẵn trong repo

### Tạo database

Ví dụ chạy PostgreSQL bằng Docker:

```bash
docker run --name polaris-postgres \
  -e POSTGRES_DB=polaris \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=12345 \
  -p 5432:5432 \
  -d postgres:15
```

### Cấu hình môi trường

Profile mặc định là `dev`.

Các biến môi trường chính:

```env
SPRING_PROFILES_ACTIVE=dev
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/polaris
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=12345
SERVER_PORT=8080
```

### Chạy ứng dụng

Windows:

```powershell
.\gradlew.bat bootRun
```

Linux/macOS:

```bash
./gradlew bootRun
```

Ứng dụng chạy tại:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

Health check:

```text
http://localhost:8080/actuator/health
```

---

## Ví dụ sử dụng API

### Tạo tenant

```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "username": "tenant_a",
    "quotaCpu": 10,
    "quotaRamMb": 8192,
    "quotaInstanceCount": 5
  }'
```

### Tạo instance

```bash
curl -X POST http://localhost:8080/api/v1/instances \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <tenant-id>" \
  -H "Idempotency-Key: create-nginx-001" \
  -d '{
    "name": "my-nginx",
    "imageName": "nginx:latest",
    "cpuAllocated": 1,
    "ramMb": 512
  }'
```

### Xem instance

```bash
curl http://localhost:8080/api/v1/instances/<instance-id> \
  -H "X-Tenant-Id: <tenant-id>"
```

### Stop instance

```bash
curl -X POST http://localhost:8080/api/v1/instances/<instance-id>/stop \
  -H "X-Tenant-Id: <tenant-id>"
```

### Delete instance

```bash
curl -X DELETE http://localhost:8080/api/v1/instances/<instance-id> \
  -H "X-Tenant-Id: <tenant-id>"
```

---

## Testing Strategy

Polaris ưu tiên test những phần có rủi ro hệ thống cao, không chỉ test getter/setter hoặc CRUD đơn giản.

Nhóm test quan trọng:

- State machine transition test
- Quota admission control test
- Tenant isolation test
- Idempotency test
- Task worker retry test
- Reconciler drift detection test
- Repository/integration test với PostgreSQL bằng Testcontainers
- API/controller test với MockMvc

Chạy test:

```bash
./gradlew test
```

Windows:

```powershell
.\gradlew.bat test
```

---

## Roadmap bản hoàn chỉnh

### Phase 1: Core resource model

- Tenant CRUD cơ bản
- Quota fields và allocated counters
- Instance create/get
- Desired/current state
- State machine nền tảng
- Flyway migration cho tenants và instances

### Phase 2: Async execution

- Tasks table
- Task service
- Worker polling bằng `FOR UPDATE SKIP LOCKED`
- Create/start/stop/delete task handlers
- Retry và failure handling

### Phase 3: Docker provider

- ComputeProvider interface
- DockerComputeProvider
- Container labels theo tenant/instance
- Provider resource tracking

### Phase 4: Reliability

- Reconciliation loop
- Stuck state detection
- Task recovery
- Quota compensation khi failure
- Idempotency keys

### Phase 5: Audit and events

- Audit logs
- Outbox events
- Outbox relay
- Instance state history

### Phase 6: Operations

- Admin endpoints
- Metrics Prometheus
- Health checks
- Structured logging
- Swagger/OpenAPI polishing

---

## Điểm nổi bật khi trình bày dự án

Polaris không chỉ là REST API quản lý container. Dự án thể hiện tư duy backend/system design qua các quyết định:

- Tách user intent và system reality bằng `desired_state` / `current_state`.
- Không gọi Docker trong HTTP transaction.
- Dùng DB-backed queue để giữ hệ thống đơn giản nhưng vẫn reliable.
- Dùng state machine để chặn lifecycle sai.
- Dùng quota admission control để bảo vệ tài nguyên multi-tenant.
- Dùng reconciler để hệ thống tự phát hiện và sửa lệch.
- Dùng outbox để tránh mất event sau commit.
- Dùng idempotency để xử lý retry an toàn.

Một cách mô tả ngắn gọn trên CV:

> Built Polaris, a lightweight container control plane inspired by AWS ECS/Kubernetes, enabling multi-tenant Docker instance management with quota admission control, desired/current state modeling, asynchronous task execution, reconciliation, audit logging, outbox events, idempotency, and Prometheus-based observability.
