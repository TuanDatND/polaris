package com.cloud.polaris.instance.api;

import com.cloud.polaris.reconcile.domain.ReconcileRequest;
import com.cloud.polaris.reconcile.domain.ReconcileRequestStatus;
import java.time.Instant;
import java.util.UUID;

public record InstanceReconciliationResponse(
        UUID instanceId,
        ReconcileRequestStatus status,
        long requestedGeneration,
        Instant availableAt,
        int failureCount
) {
    public static InstanceReconciliationResponse from(
            ReconcileRequest request
    ) {
        Instant availableAt =
                request.getStatus() == ReconcileRequestStatus.READY
                        ? request.getAvailableAt()
                        : null;

        return new InstanceReconciliationResponse(
                request.getInstanceId(),
                request.getStatus(),
                request.getRequestedGeneration(),
                availableAt,
                request.getFailureCount()
        );
    }
}