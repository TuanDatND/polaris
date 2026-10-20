package com.cloud.polaris.reconcile.listener;

import com.cloud.polaris.event.InstanceProvisionedEvent;
import com.cloud.polaris.reconcile.queue.ReconcileQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InstanceProvisionedReconcileListener {
    private final ReconcileQueueService reconcileQueueService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onInstanceProvisioned(InstanceProvisionedEvent event) {
        reconcileQueueService.wake(event.instanceId(), event.generation());
    }
}
