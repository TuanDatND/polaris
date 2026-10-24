package com.cloud.polaris.reconcile.exception;

import java.util.UUID;

public class StaleReconcileRequestOwnerException extends RuntimeException {

    public StaleReconcileRequestOwnerException(UUID instanceId) {
        super("Stale reconcile request owner: " + instanceId);
    }
}
