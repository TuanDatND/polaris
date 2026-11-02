package com.cloud.polaris.reconcile.worker;

import com.cloud.polaris.reconcile.config.ReconcileProperties;
import com.cloud.polaris.reconcile.service.ReconcileOrchestrator;
import com.cloud.polaris.reconcile.service.ReconcileQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "polaris.reconcile",
        name = "worker-enabled",
        havingValue = "true"
)
public class ReconcileWorker implements SmartLifecycle {

    private final ReconcileQueueService queueService;
    private final ReconcileOrchestrator orchestrator;
    private final ReconcileProperties properties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    private final List<ScheduledFuture<?>> laneTasks = new ArrayList<>();

    private ScheduledThreadPoolExecutor executor;
    private ReconcileLeaseRecoveryWorker recoveryWorker;

    @Override
    public void start() {
        lifecycleLock.lock();

        try {
            if (running.get()) {
                return;
            }

            if (executor != null && !executor.isTerminated()) {
                throw new IllegalStateException(
                        "Previous reconcile executor has not terminated"
                );
            }

            executor = new ScheduledThreadPoolExecutor(
                    properties.workerConcurrency(),
                    Thread.ofPlatform()
                            .name("reconcile-lane-", 0)
                            .factory()
            );

            executor.setRemoveOnCancelPolicy(true);
            executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

            if (recoveryWorker == null) {
                recoveryWorker = new ReconcileLeaseRecoveryWorker(
                        queueService,
                        properties
                );
            }

            laneTasks.clear();

            try {
                recoveryWorker.start();

                for (int laneId = 0;
                     laneId < properties.workerConcurrency();
                     laneId++) {

                    ReconcileLane lane = new ReconcileLane(
                            laneId,
                            queueService,
                            orchestrator,
                            properties,
                            running::get
                    );

                    laneTasks.add(executor.scheduleWithFixedDelay(
                            lane,
                            0,
                            properties.pollInterval().toMillis(),
                            TimeUnit.MILLISECONDS
                    ));
                }

                running.set(true);

                log.info(
                        "Started reconcile worker {} with {} lanes",
                        properties.workerId(),
                        properties.workerConcurrency()
                );
            } catch (RuntimeException exception) {
                running.set(false);
                laneTasks.forEach(task -> task.cancel(false));
                forceStopExecutors();
                throw exception;
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void stop() {
        lifecycleLock.lock();

        try {
            running.set(false);

            laneTasks.forEach(task -> task.cancel(false));

            if (executor != null) {
                executor.shutdown();
            }

            if (recoveryWorker != null) {
                recoveryWorker.requestStop();
            }

            long startedAt = System.nanoTime();
            long budgetNanos = TimeUnit.MILLISECONDS.toNanos(
                    properties.shutdownTimeout().toMillis()
            );

            try {
                boolean lanesStopped = executor == null
                        || executor.awaitTermination(
                        remainingNanos(startedAt, budgetNanos),
                        TimeUnit.NANOSECONDS
                );

                boolean recoveryStopped = recoveryWorker == null
                        || recoveryWorker.awaitTermination(
                        remainingNanos(startedAt, budgetNanos),
                        TimeUnit.NANOSECONDS
                );

                if (!lanesStopped || !recoveryStopped) {
                    log.warn(
                            "Reconcile shutdown timed out: lanesStopped={}, "
                                    + "recoveryStopped={}",
                            lanesStopped,
                            recoveryStopped
                    );

                    forceStopExecutors();
                }
            } catch (InterruptedException exception) {
                forceStopExecutors();
                Thread.currentThread().interrupt();
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    private long remainingNanos(long startedAt, long budgetNanos) {
        long elapsed = System.nanoTime() - startedAt;
        return Math.max(0L, budgetNanos - elapsed);
    }

    private void forceStopExecutors() {
        if (executor != null) {
            executor.shutdownNow();
        }

        if (recoveryWorker != null) {
            recoveryWorker.requestStop();
            recoveryWorker.forceStop();
        }
    }


}