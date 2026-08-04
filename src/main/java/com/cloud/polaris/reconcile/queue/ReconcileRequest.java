package com.cloud.polaris.reconcile.queue;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reconcile_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconcileRequest {

    @Id
    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReconcileRequestStatus status;

    @Column(name = "requested_generation", nullable = false)
    private long requestedGeneration;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public static ReconcileRequest ready(UUID instanceId, long generation, Instant now) {
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "Generation must be positive");
        }

        ReconcileRequest request = new ReconcileRequest();
        request.instanceId = Objects.requireNonNull(instanceId);
        request.status = ReconcileRequestStatus.READY;
        request.requestedGeneration = generation;
        request.availableAt = Objects.requireNonNull(now);
        request.failureCount = 0;
        return request;
    }

    public void wake(long generation, Instant now) {
        if (generation <= 0) {
            throw new IllegalArgumentException("Generation must be positive");
        }

        boolean newerGeneration = generation > requestedGeneration;
        requestedGeneration = Math.max(requestedGeneration, generation);
        if (status == ReconcileRequestStatus.RUNNING) {
            return;
        }

        status = ReconcileRequestStatus.READY;
        availableAt = now;

        if (newerGeneration) {
            failureCount = 0;
            lastError = null;
        }
    }

    public void claim(String workerId, UUID token, Instant leaseExpiresAt) {
        if (status != ReconcileRequestStatus.READY) {
            throw new IllegalStateException("Only READY request can be claimed");
        }

        status = ReconcileRequestStatus.RUNNING;
        claimedBy = Objects.requireNonNull(workerId);
        claimToken = Objects.requireNonNull(token);
        this.leaseExpiresAt = Objects.requireNonNull(leaseExpiresAt);
    }

    public boolean recoverExpiredLease(Instant now) {
        if (status != ReconcileRequestStatus.RUNNING || now.isBefore(leaseExpiresAt)) {
            return false;
        }

        clearClaim();
        status = ReconcileRequestStatus.READY;
        availableAt = now;
        failureCount++;
        return true;
    }

    public void scheduleResync(Instant nextAvailableAt) {
        requireRunning();
        clearClaim();
        status = ReconcileRequestStatus.READY;
        availableAt = nextAvailableAt;
        failureCount = 0;
        lastError = null;
    }

    public void requeue(
            Instant nextAvailableAt,
            String error
    ) {
        requireRunning();

        clearClaim();
        status = ReconcileRequestStatus.READY;
        availableAt = nextAvailableAt;
        failureCount++;
        lastError = error;
    }

    public void block(String error) {
        requireRunning();

        clearClaim();
        status = ReconcileRequestStatus.BLOCKED;
        lastError = error;
    }

    public void assertClaimToken(UUID token) {
        if (!Objects.equals(claimToken, token)) {
            throw new IllegalStateException(
                    "Stale reconcile request owner"
            );
        }
    }

    private void requireRunning() {
        if (status != ReconcileRequestStatus.RUNNING) {
            throw new IllegalStateException(
                    "Reconcile request is not running"
            );
        }
    }

    private void clearClaim() {
        claimToken = null;
        claimedBy = null;
        leaseExpiresAt = null;
    }

}
