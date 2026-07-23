package com.cloud.polaris.instance.service;

import com.cloud.polaris.common.exception.IllegalStateTransitionException;
import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.common.exception.StaleTaskOwnerException;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.domain.InstanceStateMachine;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstanceLifecycleService {
    private final InstanceRepository instanceRepository;
    private final InstanceStateMachine stateMachine;
    private final TaskRepository taskRepository;

    @Transactional
    public void ensureProvisioning(UUID instanceId) {
        Instance instance = instanceRepository.findById(instanceId).orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));

        CurrentState current = instance.getCurrentState();

        if (current == CurrentState.RUNNING) {
            return;
        }

        if (current != CurrentState.PENDING
                && current != CurrentState.PROVISIONING) {
            throw new IllegalStateTransitionException(
                    current,
                    CurrentState.PROVISIONING,
                    instance.getId()
            );
        }

        stateMachine.transitionIfNecessary(
                instance,
                CurrentState.PROVISIONING
        );

    }

    @Transactional
    public boolean markRunning(ClaimedTask claimedTask, String containerId) {
        Instance instance = assertToken(claimedTask);

        if (instance.getDesiredState() != DesiredState.RUNNING) {
            return false;
        }

        stateMachine.transitionIfNecessary(instance, CurrentState.RUNNING);
        instance.attachContainer(containerId);
        return true;
    }

    @Transactional
    public void ensureStarting(ClaimedTask claimedTask) {
        Instance instance = assertToken(claimedTask);

        if (instance.getDesiredState() != DesiredState.RUNNING) {
            return;
        }

        switch (instance.getCurrentState()) {
            case STOPPED -> stateMachine.transitionIfNecessary(instance, CurrentState.STARTING);
            case STARTING, RUNNING -> {
                //Idempotent
            }
            default -> throw new IllegalStateException("Cannot start instance from " + instance.getCurrentState());
        }
    }

    @Transactional
    public void markStopFailed(ClaimedTask claimedTask, String reason) {
        Instance instance = assertToken(claimedTask);

        if (instance.getCurrentState() == CurrentState.STOPPING) {
            stateMachine.transitionIfNecessary(instance, CurrentState.RUNNING);
        }
        instance.recordFailure(reason);
    }

    @Transactional
    public void markFailed(Instance instance, String reason) {
        stateMachine.transitionIfNecessary(
                instance,
                CurrentState.FAILED
        );

        instance.recordFailure(reason);
    }

    @Transactional
    public void ensureStopping(ClaimedTask claimedTask) {
        Instance instance = assertToken(claimedTask);

        if (instance.getDesiredState() != DesiredState.STOPPED) {
            return;
        }

        switch (instance.getCurrentState()) {
            case RUNNING, STARTING -> stateMachine.transitionIfNecessary(instance, CurrentState.STOPPING);
            case STOPPING, STOPPED -> {
                // idempotent
            }
            case PENDING, PROVISIONING -> throw new IllegalStateException("Instance has not started yet");
            default ->
                    throw new IllegalStateException("Cannot stop instance from " + instance.getCurrentState() + " state");
        }
    }

    @Transactional
    public void completeStop(ClaimedTask claimedTask, boolean resourceMissing) {
        Instance instance = assertToken(claimedTask);

        if (instance.getDesiredState() != DesiredState.STOPPED) {
            return;
        }

        switch (instance.getCurrentState()) {
            case PENDING, PROVISIONING,STARTING, STOPPING -> stateMachine.transitionIfNecessary(
                    instance,
                    CurrentState.STOPPED
            );

            case RUNNING -> {
                stateMachine.transitionIfNecessary(
                        instance,
                        CurrentState.STOPPING
                );
                stateMachine.transitionIfNecessary(
                        instance,
                        CurrentState.STOPPED
                );
            }

            case STOPPED -> {
                // idempotent
            }

            default -> throw new IllegalStateException(
                    "Cannot complete stop from "
                            + instance.getCurrentState()
            );
        }

        if (resourceMissing) {
            instance.clearContainer();
        }
    }

    @Transactional
    public void completeStartFromReconciliation(UUID instanceId, String containerId) {
        Instance instance = instanceRepository.findByIdForUpdate(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));

        if (instance.getDesiredState() != DesiredState.RUNNING) {
            return;
        }

        if (instance.getCurrentState() == CurrentState.STARTING) {
            stateMachine.transitionIfNecessary(instance, CurrentState.RUNNING);
        }

        if (instance.getCurrentState() == CurrentState.RUNNING) {
            instance.attachContainer(containerId);
        }
    }

    @Transactional
    public void completeStopFromReconciliation(UUID instanceId, boolean resourceMissing) {
        Instance instance = instanceRepository.findByIdForUpdate(instanceId).orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));
        if (instance.getDesiredState() != DesiredState.STOPPED) {
            return;
        }
        if (instance.getCurrentState() == CurrentState.STOPPING) {
            stateMachine.transitionIfNecessary(instance, CurrentState.STOPPED);
        }
        if (resourceMissing) {
            instance.clearContainer();
        }
    }

    @Transactional
    public Instance assertToken(ClaimedTask claimedTask) {
        Task task = taskRepository
                .findByIdForUpdate(claimedTask.taskId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Task not found: "
                                        + claimedTask.taskId()
                        )
                );

        if (claimedTask.claimToken() == null || !Objects.equals(task.getClaimToken(), claimedTask.claimToken())) {
            throw new StaleTaskOwnerException(task.getId().toString());
        }
        return instanceRepository.findByIdAndTenantIdForUpdate(claimedTask.instanceId(), task.getTenant().getId()).orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + claimedTask.instanceId()));
    }
}
