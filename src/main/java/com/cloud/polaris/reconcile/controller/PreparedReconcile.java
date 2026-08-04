package com.cloud.polaris.reconcile.controller;

import com.cloud.polaris.reconcile.domain.ReconcileDecision;

import java.util.UUID;

public record PreparedReconcile(
        UUID instanceId,
        long generation,
        ReconcileDecision decision,
        String providerResourceId
) {
}
