package com.cloud.polaris.reconcile.controller;

import com.cloud.polaris.common.exception.StaleReconcileRequestOwnerException;
import com.cloud.polaris.provider.NonRetryableProviderException;
import com.cloud.polaris.reconcile.domain.ProviderObservation;
import com.cloud.polaris.reconcile.domain.ReconcileDecision;
import com.cloud.polaris.reconcile.executor.ReconcileActionExecutor;
import com.cloud.polaris.reconcile.executor.ReconcileActionResult;
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

    public void reconcile(ClaimedReconcileRequest claimed) {
        PreparedReconcile prepared = null;
        try {
            ProviderObservation observation = observer.observe(claimed.instanceId());
            prepared = preparationService.prepare(claimed, observation);
            ReconcileActionResult result = ReconcileActionResult.none();
            if (requiresProviderAction(prepared.decision())) {
                if (!preparationService.executionStillCurrent(claimed, prepared)) {
                    return;
                }
                result = actionExecutor.execute(prepared);
            }
            finalizationService.complete(claimed, prepared, result);

        } catch (StaleReconcileRequestOwnerException exception) {
            log.debug("Reconcile request {} is no longer owned by this worker", claimed.instanceId());
        } catch (NonRetryableProviderException exception) {
            if (prepared == null) {
                handleFailure(claimed, exception);
                return;
            }
            try {
                finalizationService.block(claimed, prepared, summarize(exception));
            } catch (StaleReconcileRequestOwnerException staleOwner) {
                log.debug("Reconcile request {} is no longer owned by this worker", claimed.instanceId());
            } catch (Exception finalizationFailure) {
                handleFailure(claimed, finalizationFailure);
            }
        } catch (Exception exception) {
            handleFailure(claimed, exception);
        }
    }

    private boolean requiresProviderAction(ReconcileDecision decision) {
        return decision == ReconcileDecision.CREATE
                || decision == ReconcileDecision.START
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
        } catch (StaleReconcileRequestOwnerException staleOwner) {
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
