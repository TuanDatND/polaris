package com.cloud.polaris.reconcile.queue;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconcileRequestTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void should_CreateReadyRequest_withInitialGenerationAndNoClaim() {
        UUID instanceId = UUID.randomUUID();

        ReconcileRequest request = ReconcileRequest.ready(instanceId, 1L, NOW);

        assertThat(request.getInstanceId()).isEqualTo(instanceId);
        assertThat(request.getStatus()).isEqualTo(ReconcileRequestStatus.READY);
        assertThat(request.getRequestedGeneration()).isEqualTo(1L);
        assertThat(request.getAvailableAt()).isEqualTo(NOW);
        assertThat(request.getFailureCount()).isZero();
        assertThat(request.getClaimToken()).isNull();
        assertThat(request.getClaimedBy()).isNull();
        assertThat(request.getLeaseExpiresAt()).isNull();
    }

    @Test
    void should_ClaimReadyRequest_andStoreWorkerTokenAndLease() {
        ReconcileRequest request = readyRequest();
        UUID claimToken = UUID.randomUUID();
        Instant leaseExpiresAt = NOW.plusSeconds(30);

        request.claim("worker-1", claimToken, leaseExpiresAt);

        assertThat(request.getStatus()).isEqualTo(ReconcileRequestStatus.RUNNING);
        assertThat(request.getClaimToken()).isEqualTo(claimToken);
        assertThat(request.getClaimedBy()).isEqualTo("worker-1");
        assertThat(request.getLeaseExpiresAt()).isEqualTo(leaseExpiresAt);
    }

    @Test
    void should_KeepClaimOwnership_whenWakeReceivesNewGenerationDuringRun() {
        ReconcileRequest request = readyRequest();
        UUID claimToken = UUID.randomUUID();
        Instant leaseExpiresAt = NOW.plusSeconds(30);
        request.claim("worker-1", claimToken, leaseExpiresAt);

        request.wake(2L, NOW.plusSeconds(5));

        assertThat(request.getRequestedGeneration()).isEqualTo(2L);
        assertThat(request.getStatus()).isEqualTo(ReconcileRequestStatus.RUNNING);
        assertThat(request.getClaimToken()).isEqualTo(claimToken);
        assertThat(request.getClaimedBy()).isEqualTo("worker-1");
        assertThat(request.getLeaseExpiresAt()).isEqualTo(leaseExpiresAt);
    }

    @Test
    void should_NotRecoverLease_beforeItExpires() {
        ReconcileRequest request = readyRequest();
        Instant leaseExpiresAt = NOW.plusSeconds(30);
        request.claim("worker-1", UUID.randomUUID(), leaseExpiresAt);

        boolean recovered = request.recoverExpiredLease(leaseExpiresAt.minusNanos(1));

        assertThat(recovered).isFalse();
        assertThat(request.getStatus()).isEqualTo(ReconcileRequestStatus.RUNNING);
        assertThat(request.getFailureCount()).isZero();
    }

    @Test
    void should_RecoverExpiredLease_andRequeueRequest() {
        ReconcileRequest request = readyRequest();
        Instant leaseExpiresAt = NOW.plusSeconds(30);
        request.claim("worker-1", UUID.randomUUID(), leaseExpiresAt);

        boolean recovered = request.recoverExpiredLease(leaseExpiresAt);

        assertThat(recovered).isTrue();
        assertThat(request.getStatus()).isEqualTo(ReconcileRequestStatus.READY);
        assertThat(request.getAvailableAt()).isEqualTo(leaseExpiresAt);
        assertThat(request.getClaimToken()).isNull();
        assertThat(request.getClaimedBy()).isNull();
        assertThat(request.getLeaseExpiresAt()).isNull();
        assertThat(request.getFailureCount()).isEqualTo(1);
    }

    @Test
    void should_ClearClaimAndResetFailures_whenSchedulingResync() {
        ReconcileRequest request = readyRequest();
        request.claim("worker-1", UUID.randomUUID(), NOW.plusSeconds(30));
        request.requeue(NOW.plusSeconds(5), "provider timeout");
        request.claim("worker-2", UUID.randomUUID(), NOW.plusSeconds(35));
        Instant nextResyncAt = NOW.plusSeconds(60);

        request.scheduleResync(nextResyncAt);

        assertThat(request.getStatus()).isEqualTo(ReconcileRequestStatus.READY);
        assertThat(request.getAvailableAt()).isEqualTo(nextResyncAt);
        assertThat(request.getFailureCount()).isZero();
        assertThat(request.getLastError()).isNull();
        assertThat(request.getClaimToken()).isNull();
        assertThat(request.getClaimedBy()).isNull();
        assertThat(request.getLeaseExpiresAt()).isNull();
    }

    @Test
    void should_RejectStaleClaimToken() {
        ReconcileRequest request = readyRequest();
        request.claim("worker-1", UUID.randomUUID(), NOW.plusSeconds(30));

        assertThatThrownBy(() -> request.assertClaimToken(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stale reconcile request owner");
    }

    private ReconcileRequest readyRequest() {
        return ReconcileRequest.ready(UUID.randomUUID(), 1L, NOW);
    }
}
