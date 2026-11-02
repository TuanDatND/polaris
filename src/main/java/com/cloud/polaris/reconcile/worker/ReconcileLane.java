package com.cloud.polaris.reconcile.worker;

import com.cloud.polaris.reconcile.config.ReconcileProperties;
import com.cloud.polaris.reconcile.service.ReconcileOrchestrator;
import com.cloud.polaris.reconcile.service.ReconcileQueueService;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.cloud.polaris.reconcile.service.model.ClaimedReconcileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public final class ReconcileLane implements Runnable {

    private final int laneId;
    private final ReconcileQueueService queueService;
    private final ReconcileOrchestrator orchestrator;
    private final ReconcileProperties properties;
    private final BooleanSupplier acceptingWork;

    @Override
    public void run() {
        try {
            if (!acceptingWork.getAsBoolean()) {
                return;
            }

            List<ClaimedReconcileRequest> requests = queueService.claimReady(
                    1,
                    properties.workerId(),
                    properties.leaseDuration()
            );

            for (ClaimedReconcileRequest request : requests) {
                log.debug(
                        "Reconcile lane {} processing instance {}",
                        laneId,
                        request.instanceId()
                );

                orchestrator.reconcile(request);
            }
        } catch (Exception exception) {
            log.error(
                    "Reconcile lane {} failed for worker {}",
                    laneId,
                    properties.workerId(),
                    exception
            );
        }
    }
}