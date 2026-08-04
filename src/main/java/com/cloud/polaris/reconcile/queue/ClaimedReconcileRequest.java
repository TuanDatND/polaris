package com.cloud.polaris.reconcile.queue;

import java.util.UUID;

public record ClaimedReconcileRequest(
        UUID instanceId,
        long requestedGeneration,
        UUID claimToken
) {
}
