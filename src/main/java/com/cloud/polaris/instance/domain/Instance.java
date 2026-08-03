package com.cloud.polaris.instance.domain;

import com.cloud.polaris.reconcile.domain.ProviderObservedState;
import com.cloud.polaris.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(name = "instances")
public class Instance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_observed_state", nullable = false)
    private ProviderObservedState lastObservedState;

    @Column(name = "last_observed_at")
    private Instant lastObservedAt;

    @Column(name = "generation", nullable = false)
    private long generation;

    @Column(name = "observed_generation", nullable = false)
    private long observedGeneration;

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

    @Column(name = "quota_released", nullable = false)
    private boolean quotaReleased;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public static Instance createPending(Tenant tenant, String name, String imageName, Integer cpuAllocated, Integer ramMb) {
        Instance instance = new Instance();

        instance.tenant = tenant;
        instance.name = name;
        instance.imageName = imageName;
        instance.cpuAllocated = cpuAllocated;
        instance.ramMb = ramMb;

        instance.generation = 1L;
        instance.observedGeneration = 0L;
        instance.lastObservedState = ProviderObservedState.UNKNOWN;

        instance.currentState = CurrentState.PENDING;
        instance.desiredState = DesiredState.RUNNING;
        return instance;
    }


    public boolean requestStart() {
        return changeDesiredState(DesiredState.RUNNING);
    }

    public boolean requestStop() {
        return changeDesiredState(DesiredState.STOPPED);
    }

    public boolean requestDelete() {
        return changeDesiredState(DesiredState.DELETED);
    }

    void changeCurrentState(CurrentState currentState) {
        this.currentState = currentState;
    }

    //for provider
    public void attachContainer(String containerId) {
        this.containerId = containerId;
    }

    public void clearContainer() {
        this.containerId = null;
    }

    public void recordFailure(String reason) {
        this.failureReason = reason;
    }

    public boolean releaseQuota() {
        if (this.quotaReleased) {
            return false;
        }
        quotaReleased = true;
        return true;
    }

    public void recordObservation(
            ProviderObservedState observedState,
            Instant observedAt
    ) {
        this.lastObservedState = observedState;
        this.lastObservedAt = observedAt;
    }

    public void markObservedGeneration(long generation) {
        if (generation > this.generation) {
            throw new IllegalArgumentException(
                    "Observed generation cannot exceed desired generation"
            );
        }

        this.observedGeneration =
                Math.max(this.observedGeneration, generation);
    }

    private boolean changeDesiredState(DesiredState nextState) {
        if (desiredState == DesiredState.DELETED && nextState != DesiredState.DELETED) {
            throw new IllegalStateException(
                    "Cannot change desired state after deletion"
            );
        }

        if (desiredState == nextState) {
            return false;
        }

        desiredState = nextState;
        generation++;
        return true;
    }


}
