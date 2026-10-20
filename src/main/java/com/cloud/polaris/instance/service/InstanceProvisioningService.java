package com.cloud.polaris.instance.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.common.exception.StaleTaskOwnerException;
import com.cloud.polaris.instance.domain.CurrentState;
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

@Service
@RequiredArgsConstructor
public class InstanceProvisioningService {
    private final InstanceRepository instanceRepository;
    private final InstanceStateMachine stateMachine;
    private final TaskRepository taskRepository;

    @Transactional
    public void ensureProvisioning(ClaimedTask claimedTask) {
        Instance instance = assertToken(claimedTask);

        switch (instance.getCurrentState()) {
            case PENDING -> stateMachine.transition(
                    instance,
                    CurrentState.PROVISIONING
            );

            case PROVISIONING, STOPPED -> {
                // Idempotent retry.
            }

            default -> throw new IllegalStateException(
                    "Cannot provision instance from "
                            + instance.getCurrentState()
            );
        }
    }

    @Transactional
    public void completeProvisioning(
            ClaimedTask claimedTask,
            String providerResourceId
    ) {
        if (providerResourceId == null || providerResourceId.isBlank()) {
            throw new IllegalArgumentException(
                    "Provider resource id must not be blank"
            );
        }

        Instance instance = assertToken(claimedTask);

        switch (instance.getCurrentState()) {
            case PENDING, PROVISIONING -> stateMachine.transitionIfNecessary(
                    instance,
                    CurrentState.STOPPED
            );

            case STOPPED -> {
                // Idempotent retry.
            }

            default -> throw new IllegalStateException(
                    "Cannot complete provisioning from "
                            + instance.getCurrentState()
            );
        }

        instance.attachContainer(providerResourceId);
    }


    @Transactional
    public void markFailed(Instance instance, String reason) {
        stateMachine.transitionIfNecessary(
                instance,
                CurrentState.FAILED
        );

        instance.recordFailure(reason);
    }


    private Instance assertToken(ClaimedTask claimedTask) {
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
