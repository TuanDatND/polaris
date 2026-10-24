package com.cloud.polaris.reconcile.service;

import com.cloud.polaris.provider.NonRetryableProviderException;
import com.cloud.polaris.reconcile.domain.ProviderObservation;
import com.cloud.polaris.reconcile.domain.ReconcileDecision;
import com.cloud.polaris.reconcile.exception.StaleReconcileRequestOwnerException;
import com.cloud.polaris.reconcile.provider.InstanceProviderObserver;
import com.cloud.polaris.reconcile.provider.ReconcileActionExecutor;
import com.cloud.polaris.reconcile.service.model.ClaimedReconcileRequest;
import com.cloud.polaris.reconcile.service.model.PreparedReconcile;
import com.cloud.polaris.reconcile.service.model.ReconcileActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconcileOrchestrator {
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
