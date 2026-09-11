package com.cloud.polaris.reconcile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix ="polaris.reconcile")
public record ReconcileProperties(
        String workerId,
        int claimBatchSize,
        Duration leaseDuration,
        int recoveryBatchSize
) {
}
