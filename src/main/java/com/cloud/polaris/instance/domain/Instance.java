package com.cloud.polaris.instance.domain;

import com.cloud.polaris.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "instances")
public class Instance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "image_name", nullable = false)
    private String imageName;

    @Column(name = "cpu_allocated", nullable = false)
    private Integer cpuAllocated;

    @Column(name = "ram_mb", nullable = false)
    private Integer ramMb;

    @Enumerated(EnumType.STRING)
    @Column(name = "desired_state", nullable = false)
    private DesiredState desiredState;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state", nullable = false)
    private CurrentState currentState;

    @Column(name = "container_id")
    private String containerId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
