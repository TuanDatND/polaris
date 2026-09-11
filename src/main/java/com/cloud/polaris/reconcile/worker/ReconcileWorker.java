package com.cloud.polaris.reconcile.worker;

import com.cloud.polaris.reconcile.config.ReconcileProperties;
import com.cloud.polaris.reconcile.controller.ReconcileController;
import com.cloud.polaris.reconcile.queue.ReconcileQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "polaris.reconcile",
        name = "worker-enabled",
        havingValue = "true")
public class ReconcileWorker {

    private final ReconcileQueueService reconcileQueueService;
    private final ReconcileController reconcileController;
    private final ReconcileProperties reconcileProperties;

    @Scheduled(fixedDelayString = "${polaris.reconcile.poll-interval:1s}")
    public void poll() {
        try {
            reconcileQueueService.recoverExpiredLeases(
                    reconcileProperties.recoveryBatchSize()
            );

            reconcileQueueService.claimReady(
                    reconcileProperties.claimBatchSize(),
                    reconcileProperties.workerId(),
                    reconcileProperties.leaseDuration()
            ).forEach(reconcileController::reconcile);
        } catch (Exception exception) {
            log.error("Reconcile worker poll failed", exception);
        }
    }
}
