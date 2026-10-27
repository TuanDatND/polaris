package com.cloud.polaris.reconcile.worker;

import com.cloud.polaris.reconcile.config.ReconcileProperties;
import com.cloud.polaris.reconcile.service.ReconcileQueueService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ReconcileLeaseRecoveryWorker {
    private final ReconcileQueueService queueService;
    private final ReconcileProperties properties;

    private volatile ScheduledThreadPoolExecutor executor;
    private ScheduledFuture<?> recoveryTask;

    public synchronized void start() {
        if (executor != null) {
            if (!executor.isShutdown()) {
                return;
            }

            if (!executor.isTerminated()) {
                throw new IllegalStateException(
                        "Previous recovery executor has not terminated"
                );
            }
        }

        ScheduledThreadPoolExecutor candidate =
                new ScheduledThreadPoolExecutor(
                        1,
                        Thread.ofPlatform()
                                .name("reconcile-recovery-", 0)
                                .factory()
                );

        candidate.setRemoveOnCancelPolicy(true);
        candidate.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        candidate.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

        try {
            recoveryTask = candidate.scheduleWithFixedDelay(
                    this::recoverSafely,
                    0,
                    properties.recoveryInterval().toMillis(),
                    TimeUnit.MILLISECONDS
            );

            executor = candidate;
        } catch (RuntimeException exception) {
            candidate.shutdownNow();
            throw exception;
        }
    }

    public synchronized void requestStop() {
        if (recoveryTask != null) {
            recoveryTask.cancel(false);
        }

        if (executor != null) {
            executor.shutdown();
        }
    }

    public boolean awaitTermination(
            long timeout,
            TimeUnit unit
    ) throws InterruptedException {
        ScheduledThreadPoolExecutor current = executor;

        return current == null
                || current.awaitTermination(timeout, unit);
    }

    public synchronized void forceStop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void recoverSafely() {
        try {
            int recovered = queueService.recoverExpiredLeases(
                    properties.recoveryBatchSize()
            );

            if (recovered > 0) {
                log.info(
                        "Recovered {} expired reconcile leases",
                        recovered
                );
            }
        } catch (Exception exception) {
            log.error(
                    "Reconcile lease recovery failed",
                    exception
            );
        }
    }

}
