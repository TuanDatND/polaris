package com.cloud.polaris.instance.service;

import com.cloud.polaris.common.exception.IllegalStateTransitionException;
import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.domain.InstanceStateMachine;
import com.cloud.polaris.instance.repository.InstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstanceLifecycleService {
    private final InstanceRepository instanceRepository;
    private final InstanceStateMachine stateMachine;

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
    public void markRunning(UUID instanceId, String containerId) {
        Instance instance =  instanceRepository.findById(instanceId).orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));

        stateMachine.transitionIfNecessary(instance, CurrentState.RUNNING);
        instance.attachContainer(containerId);
    }

    @Transactional
    public void markFailed(Instance instance, String reason) {
        stateMachine.transitionIfNecessary(
                instance,
                CurrentState.FAILED
        );

        instance.recordFailure(reason);
    }
}
