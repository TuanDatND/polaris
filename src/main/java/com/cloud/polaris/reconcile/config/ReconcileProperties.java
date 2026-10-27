package com.cloud.polaris.reconcile.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix ="polaris.reconcile")
public record ReconcileProperties(
        @NotBlank String workerId,
        @Min(1) int claimBatchSize,
        Duration leaseDuration,
        @Min(1) int recoveryBatchSize,
        @Min(1) int workerConcurrency,
        Duration pollInterval,
        Duration recoveryInterval,
        Duration shutdownTimeout
) {
    public ReconcileProperties {
        requireValidDuration(leaseDuration, "lease-duration");
        requireValidDuration(pollInterval, "poll-interval");
        requireValidDuration(recoveryInterval, "recovery-interval");
        requireValidDuration(shutdownTimeout, "shutdown-timeout");
    }

    private static void requireValidDuration(
            Duration value,
            String property
    ) {
        if (value == null
                || value.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException(
                    "polaris.reconcile." + property
                            + " must be at least 1ms"
            );
        }

        try {
            value.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "polaris.reconcile." + property + " is too large",
                    exception
            );
        }
    }
}
