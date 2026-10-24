package com.cloud.polaris.reconcile.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix ="polaris.reconcile")
public record ReconcileProperties(
        String workerId,
        int claimBatchSize,
        Duration leaseDuration,
        int recoveryBatchSize
) {
}
