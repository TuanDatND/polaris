package com.cloud.polaris.reconcile.controller;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.domain.InstanceStateMachine;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.reconcile.queue.ClaimedReconcileRequest;
import com.cloud.polaris.reconcile.queue.ReconcileRequest;
import com.cloud.polaris.reconcile.queue.ReconcileRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReconcileFinalizationService {


    private static final Duration ACTION_RECHECK_DELAY = Duration.ofSeconds(1);
    private static final Duration WAIT_RECHECK_DELAY = Duration.ofSeconds(5);
    private static final Duration RESYNC_DELAY = Duration.ofSeconds(30);

    private final InstanceRepository instanceRepository;
    private final ReconcileRequestRepository reconcileRequestRepository;
    private final InstanceStateMachine stateMachine;

    @Transactional
    public boolean complete(ClaimedReconcileRequest claimed, PreparedReconcile prepared) {
        Instance instance = instanceRepository.findByIdForUpdate(claimed.instanceId()).orElseThrow(
                () -> new ResourceNotFoundException("Instance not found" + claimed.instanceId())
        );

        ReconcileRequest request = reconcileRequestRepository.findByInstanceIdForUpdate(claimed.instanceId()).orElseThrow(() -> new ResourceNotFoundException("Reconcile request not found: " + claimed.instanceId()));

        request.assertClaimToken(claimed.claimToken());

        Instant now = Instant.now();

        if (instance.getGeneration() != prepared.generation() || request.getRequestedGeneration() != prepared.generation()) {
            request.scheduleResync(now);
            return false;
        }

        return switch (prepared.decision()) {
            case NOOP -> {
                projectConvergedState(instance, prepared);
                instance.markObservedGeneration(prepared.generation());
                request.scheduleResync(now.plus(RESYNC_DELAY));

                yield instance.getCurrentState() == CurrentState.DELETED
                        && !instance.isQuotaReleased();
            }

            case START, STOP, DELETE -> {
                request.scheduleResync(now.plus(ACTION_RECHECK_DELAY));
                yield false;
            }

            case WAIT -> {
                request.scheduleResync(now.plus(WAIT_RECHECK_DELAY));
                yield false;
            }

            case INVALID -> {
                request.block("Invalid desired/current/observed state combination");
                yield false;
            }
        };
    }

    private void projectConvergedState(Instance instance, PreparedReconcile prepared) {
        switch (instance.getDesiredState()) {
            case RUNNING -> projectRunning(instance, prepared);
            case STOPPED -> projectStopped(instance, prepared);
            case DELETED -> projectDeleted(instance);
        }
    }

    private void projectRunning(Instance instance, PreparedReconcile prepared) {
        switch (instance.getCurrentState()) {
            case STOPPED -> {
                stateMachine.transition(instance, CurrentState.STARTING);
                stateMachine.transition(instance, CurrentState.RUNNING);
            }
            case STARTING, STOPPING -> stateMachine.transition(instance, CurrentState.RUNNING);
            case RUNNING -> {
                // Already converged.
            }
            default -> throw new IllegalStateException(
                    "Cannot project RUNNING from "
                            + instance.getCurrentState()
            );
        }

        instance.attachContainer(requireProviderResourceId(prepared));
    }


    private void projectStopped(Instance instance, PreparedReconcile prepared) {
        switch (instance.getCurrentState()) {
            case RUNNING -> {
                stateMachine.transition(instance, CurrentState.STOPPING);
                stateMachine.transition(instance, CurrentState.STOPPED);
            }
            case STARTING, STOPPING -> stateMachine.transition(instance, CurrentState.STOPPED);
            case STOPPED -> {
                // Already converged.
            }
            default -> throw new IllegalStateException(
                    "Cannot project STOPPED from "
                            + instance.getCurrentState()
            );
        }

        if (prepared.providerResourceId() == null) {
            instance.clearContainer();
        } else {
            instance.attachContainer(prepared.providerResourceId());
        }
    }

    private void projectDeleted(Instance instance) {
        switch (instance.getCurrentState()) {
            case STARTING -> {
                stateMachine.transition(instance, CurrentState.STOPPING);
                stateMachine.transition(instance, CurrentState.STOPPED);
                stateMachine.transition(instance, CurrentState.DELETING);
                stateMachine.transition(instance, CurrentState.DELETED);
            }
            case RUNNING, STOPPED, FAILED -> {
                stateMachine.transition(instance, CurrentState.DELETING);
                stateMachine.transition(instance, CurrentState.DELETED);
            }
            case STOPPING -> {
                stateMachine.transition(instance, CurrentState.STOPPED);
                stateMachine.transition(instance, CurrentState.DELETING);
                stateMachine.transition(instance, CurrentState.DELETED);
            }
            case DELETING -> stateMachine.transition(instance, CurrentState.DELETED);
            case DELETED -> {
                // Already converged.
            }
            default -> throw new IllegalStateException(
                    "Cannot project DELETED from "
                            + instance.getCurrentState()
            );
        }

        instance.clearContainer();
    }

    private String requireProviderResourceId(PreparedReconcile prepared) {
        if (prepared.providerResourceId() == null
                || prepared.providerResourceId().isBlank()) {
            throw new IllegalStateException(
                    "RUNNING observation requires a provider resource id"
            );
        }

        return prepared.providerResourceId();
    }
}
