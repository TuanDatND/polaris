package com.cloud.polaris.reconcile.controller;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.domain.InstanceStateMachine;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.reconcile.domain.InstanceSnapshot;
import com.cloud.polaris.reconcile.domain.ProviderObservation;
import com.cloud.polaris.reconcile.domain.ReconcileDecision;
import com.cloud.polaris.reconcile.planner.InstanceReconcilePlanner;
import com.cloud.polaris.reconcile.queue.ClaimedReconcileRequest;
import com.cloud.polaris.reconcile.queue.ReconcileRequest;
import com.cloud.polaris.reconcile.queue.ReconcileRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReconcilePreparationService {

    private final InstanceRepository instanceRepository;
    private final ReconcileRequestRepository reconcileRequestRepository;
    private final InstanceStateMachine stateMachine;
    private final InstanceReconcilePlanner planner;

    @Transactional
    public PreparedReconcile prepare(ClaimedReconcileRequest claimed, ProviderObservation observation) {
        Instance instance = instanceRepository
                .findByIdForUpdate(claimed.instanceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instance not found: " + claimed.instanceId()
                ));

        ReconcileRequest request = reconcileRequestRepository.findByInstanceIdForUpdate(claimed.instanceId())
                .orElseThrow(() -> new ResourceNotFoundException( "Reconcile request not found: " + claimed.instanceId()));

        request.assertClaimToken(claimed.claimToken());

        instance.recordObservation(observation.state(), Instant.now());

        InstanceSnapshot snapshot = new InstanceSnapshot(
                instance.getDesiredState(),
                instance.getCurrentState(),
                observation.state(),
                isProvisioningActive(instance)
        );

        ReconcileDecision decision = planner.plan(snapshot);
        moveToInProgressState(instance, decision);
        return new PreparedReconcile(
                instance.getId(),
                instance.getGeneration(),
                decision,
                observation.providerResourceId()
        );
    }

    private boolean isProvisioningActive(Instance instance) {
        return instance.getCurrentState() == CurrentState.PENDING
                || instance.getCurrentState() == CurrentState.PROVISIONING;
    }

    private void moveToInProgressState(
            Instance instance,
            ReconcileDecision decision
    ) {
        switch (decision) {
            case START -> stateMachine.transitionIfNecessary(
                    instance,
                    CurrentState.STARTING
            );

            case STOP -> {
                if (instance.getCurrentState() != CurrentState.DELETING) {
                    stateMachine.transitionIfNecessary(
                            instance,
                            CurrentState.STOPPING
                    );
                }
            }

            case DELETE -> stateMachine.transitionIfNecessary(
                    instance,
                    CurrentState.DELETING
            );

            case NOOP, WAIT, INVALID -> {
                // Không đổi current state ở prepare.
            }
        }
    }
}
