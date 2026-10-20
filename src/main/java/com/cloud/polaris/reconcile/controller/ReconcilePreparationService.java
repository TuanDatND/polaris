package com.cloud.polaris.reconcile.controller;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.domain.InstanceStateMachine;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.reconcile.domain.*;
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
                .orElseThrow(() -> new ResourceNotFoundException("Reconcile request not found: " + claimed.instanceId()));

        request.assertClaimToken(claimed.claimToken());

        instance.recordObservation(observation.state(), Instant.now());
        adoptObservedResource(instance, observation);

        InstanceSnapshot snapshot = new InstanceSnapshot(
                instance.getDesiredState(),
                instance.getCurrentState(),
                observation.state(),
                instance.isProvisioned()
        );

        ReconcileDecision decision = planner.plan(snapshot);
        moveToInProgressState(instance, decision);
        return new PreparedReconcile(
                instance.getId(),
                instance.getGeneration(),
                decision,
                observation.state(),
                observation.providerResourceId(),
                decision == ReconcileDecision.CREATE
                        ? new InstanceProvisioningSpec(
                        instance.getId(),
                        instance.getTenant().getId(),
                        instance.getImageName(),
                        instance.getCpuAllocated(),
                        instance.getRamMb()
                )
                        : null
        );
    }

    private void adoptObservedResource(Instance instance, ProviderObservation observation) {
        if (observation.state() != ProviderObservedState.CREATED
                && observation.state() != ProviderObservedState.RUNNING
                && observation.state() != ProviderObservedState.STOPPED) {
            return;
        }

        // Recover a successful create even if its worker crashed before finalization.
        // Never attach an unexpected resource to a terminal instance.
        if (instance.getCurrentState() == CurrentState.DELETED) {
            return;
        }
        instance.recordProvisioned(observation.providerResourceId());
        if (instance.getCurrentState() == CurrentState.PENDING
                || instance.getCurrentState() == CurrentState.PROVISIONING) {
            stateMachine.transition(instance, CurrentState.STOPPED);
        }
    }

    @Transactional
    public boolean executionStillCurrent(ClaimedReconcileRequest claimed, PreparedReconcile prepared) {

        Instance instance = instanceRepository.findByIdForUpdate(claimed.instanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + claimed.instanceId()));

        ReconcileRequest request = reconcileRequestRepository.findByInstanceIdForUpdate(claimed.instanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Reconcile request not found: " + claimed.instanceId()));

        request.assertClaimToken(claimed.claimToken());

        if (instance.getGeneration() != prepared.generation() || request.getRequestedGeneration() != prepared.generation()) {
            request.scheduleResync(Instant.now());

            return false;
        }
        return true;
    }

    private void moveToInProgressState(
            Instance instance,
            ReconcileDecision decision
    ) {
        // A late observation may find a resource after the DB reached DELETED.
        // Cleanup is still allowed, but terminal state must never move backwards.
        if (instance.getCurrentState() == CurrentState.DELETED
                && instance.getDesiredState() == DesiredState.DELETED) {
            return;
        }
        switch (decision) {
            case CREATE -> stateMachine.transitionIfNecessary(instance, CurrentState.PROVISIONING);

            case START -> stateMachine.transitionIfNecessary(
                    instance,
                    CurrentState.STARTING
            );

            case STOP -> {
                if (instance.getCurrentState() == CurrentState.FAILED
                        && instance.getDesiredState() == DesiredState.DELETED) {
                    stateMachine.transitionIfNecessary(
                            instance,
                            CurrentState.DELETING
                    );
                } else if (instance.getCurrentState() != CurrentState.DELETING) {
                    stateMachine.transitionIfNecessary(
                            instance,
                            CurrentState.STOPPING
                    );
                }
            }

            case DELETE -> {
                if (instance.getCurrentState() == CurrentState.STARTING
                        || instance.getCurrentState() == CurrentState.STOPPING) {
                    stateMachine.transition(
                            instance,
                            CurrentState.STOPPED
                    );
                }

                stateMachine.transitionIfNecessary(
                        instance,
                        CurrentState.DELETING
                );
            }

            case NOOP, WAIT, INVALID -> {
            }
        }
    }
}