package com.cloud.polaris.reconcile.controller;

import com.cloud.polaris.instance.service.InstanceCompensationService;
import com.cloud.polaris.reconcile.domain.ProviderObservation;
import com.cloud.polaris.reconcile.domain.ReconcileDecision;
import com.cloud.polaris.reconcile.executor.ReconcileActionExecutor;
import com.cloud.polaris.reconcile.observer.InstanceProviderObserver;
import com.cloud.polaris.reconcile.queue.ClaimedReconcileRequest;
import com.cloud.polaris.reconcile.queue.ReconcileQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconcileController {
    private static final Duration FAILURE_RETRY_DELAY = Duration.ofSeconds(5);

    private final InstanceProviderObserver observer;
    private final ReconcilePreparationService preparationService;
    private final ReconcileActionExecutor actionExecutor;
    private final ReconcileFinalizationService finalizationService;
    private final ReconcileQueueService reconcileQueueService;
    private final InstanceCompensationService compensationService;

    public void reconcile(ClaimedReconcileRequest claimed) {
        try{
            ProviderObservation observation = observer.observe(claimed.instanceId());
            PreparedReconcile prepared = preparationService.prepare(claimed, observation);
            if (requiresProviderAction(prepared.decision())){
                actionExecutor.execute(prepared);
            }
            boolean quotaReleaseRequired = finalizationService.complete(claimed, prepared);

            if (quotaReleaseRequired) {
                compensationService.releaseQuotaIfCleanupCompleted(claimed.instanceId());
            }
        }catch (Exception exception){
            handleFailure(claimed, exception);
        }
    }

    private boolean requiresProviderAction(ReconcileDecision decision) {
        return decision == ReconcileDecision.START
                || decision == ReconcileDecision.STOP
                || decision == ReconcileDecision.DELETE;
    }

    private void handleFailure(
            ClaimedReconcileRequest claimed,
            Exception exception
    ) {
        log.warn(
                "Reconcile failed for instance {}",
                claimed.instanceId(),
                exception
        );

        try {
            reconcileQueueService.requeue(
                    claimed,
                    Instant.now().plus(FAILURE_RETRY_DELAY),
                    summarize(exception)
            );
        }catch (IllegalStateException staleOwner){
            log.debug("Reconcile request {} is no longer owned by this worker", claimed.instanceId());
        }
    }

    private String summarize(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return exception.getClass().getSimpleName() + ": " + message;
    }
}
