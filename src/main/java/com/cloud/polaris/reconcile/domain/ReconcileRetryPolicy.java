package com.cloud.polaris.reconcile.domain;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ReconcileRetryPolicy {

    private static final long INITIAL_DELAY_SECONDS = 5;
    private static final long MAX_DELAY_SECONDS = 120;

    public Duration delayAfterFailures(int previousFailureCount, double jitterUnit) {
        if (previousFailureCount < 0) {
            throw new IllegalArgumentException(
                    "Previous failure count must not be negative"
            );
        }

        if (!Double.isFinite(jitterUnit)
                || jitterUnit < 0
                || jitterUnit >= 1) {
            throw new IllegalArgumentException(
                    "Jitter unit must be finite and in [0, 1)"
            );
        }

        long seconds = INITIAL_DELAY_SECONDS;

        for (int i = 0; i < previousFailureCount && seconds < MAX_DELAY_SECONDS; i++) {
            seconds = Math.min(seconds * 2, MAX_DELAY_SECONDS);
        }

        long baseMillis = Duration.ofSeconds(seconds).toMillis();
        long halfMillis = baseMillis / 2;
        long extraMillis = (long) (halfMillis * jitterUnit);

        return Duration.ofMillis(halfMillis + extraMillis);
    }
}
