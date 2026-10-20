package com.cloud.polaris.reconcile.executor;

public record ReconcileActionResult(String createdResourceId) {

    public static ReconcileActionResult none() {
        return new ReconcileActionResult(null);
    }

    public static ReconcileActionResult created(String providerResourceId) {
        if (providerResourceId == null || providerResourceId.isBlank()) {
            throw new IllegalArgumentException("Created resource id must not be blank");
        }
        return new ReconcileActionResult(providerResourceId);
    }
}
