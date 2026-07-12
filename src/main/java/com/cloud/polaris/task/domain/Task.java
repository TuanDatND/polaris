package com.cloud.polaris.task.domain;

import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.tenant.domain.Tenant;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    private Instance instance;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    //    Factory method
    public static Task createInstanceTask(Tenant tenant, Instance instance, JsonNode payload) {
        Task task = new Task();

        task.tenant = tenant;
        task.instance = instance;
        task.type = TaskType.CREATE_INSTANCE;
        task.status = TaskStatus.QUEUED;
        task.attempts = 0;
        task.maxAttempts = 5;
        task.availableAt = Instant.now();
        task.idempotencyKey = "create-instance:" + instance.getId();;
        task.payload = payload;

        return task;
    }

    public void claim(String workerId, Instant now){
       if(status != TaskStatus.QUEUED){
           throw new IllegalStateException("Task is not queued");
       }

       status = TaskStatus.RUNNING;
       attempts++;
       lockedAt = now;
       lockedBy = workerId;
    }
}
