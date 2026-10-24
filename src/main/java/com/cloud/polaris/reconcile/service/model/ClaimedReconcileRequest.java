package com.cloud.polaris.reconcile.service.model;

import java.util.UUID;

public record ClaimedReconcileRequest(
        UUID instanceId,
        long requestedGeneration,
        UUID claimToken
) {
}
