package com.cloud.polaris.task.handler;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.instance.service.InstanceLifecycleService;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.provider.ProviderResourceStatus;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StartInstanceHandler implements TaskHandler {

    private final InstanceRepository instanceRepository;
    private final InstanceLifecycleService instanceLifecycleService;
    private final ComputeProvider computeProvider;

    public StartInstanceHandler(InstanceRepository instanceRepository, InstanceLifecycleService instanceLifecycleService, ComputeProvider computeProvider) {
        this.instanceRepository = instanceRepository;
        this.instanceLifecycleService = instanceLifecycleService;
        this.computeProvider = computeProvider;
    }

    @Override
    public TaskType supportedType() {
        return TaskType.START_INSTANCE;
    }

    @Override
    public void handle(ClaimedTask claimedTask) {
        Instance instance = instanceRepository
                .findById(claimedTask.instanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + claimedTask.instanceId()));

        if (instance.getDesiredState() != DesiredState.RUNNING) {
            return;
        }
        if (instance.getCurrentState() == CurrentState.RUNNING) {
            return;
        }

        if (instance.getCurrentState() != CurrentState.STOPPED && instance.getCurrentState() != CurrentState.STARTING) {
            throw new IllegalStateException(
                    "Cannot start instance from "
                            + instance.getCurrentState()
            );
        }

        instanceLifecycleService.ensureStarting(claimedTask);

        Optional<ProviderResource> resource = computeProvider.findByInstanceId(claimedTask.instanceId());

        if (resource.isEmpty()) {
            throw new IllegalStateException("Provider resource not found for instance: " + claimedTask.instanceId());
        }

        ProviderResource providerResource = resource.get();

        if (providerResource.status() == ProviderResourceStatus.UNKNOWN) {
            throw new IllegalStateException(
                    "Provider resource status is unknown"
            );
        }

        if (providerResource.status() == ProviderResourceStatus.RUNNING) {
            boolean markedRunning = instanceLifecycleService.markRunning(
                    claimedTask,
                    providerResource.providerResourceId()
            );

            if (!markedRunning) {
                throw new IllegalStateException(
                        "Instance desired state changed before marking RUNNING"
                );
            }

            return;
        }

        if (providerResource.status() == ProviderResourceStatus.STOPPED || providerResource.status() == ProviderResourceStatus.CREATED) {
            computeProvider.start(providerResource.providerResourceId());

            Optional<ProviderResource> afterStart = computeProvider.findByInstanceId(claimedTask.instanceId());

            if (afterStart.isEmpty()) {
                throw new IllegalStateException("Provider resource disappeared after start " + claimedTask.instanceId());
            }

            if (afterStart.get().status() == ProviderResourceStatus.RUNNING) {
                boolean markedRunning = instanceLifecycleService.markRunning(
                        claimedTask,
                        afterStart.get().providerResourceId()
                );

                if (!markedRunning) {
                    throw new IllegalStateException(
                            "Instance desired state changed before marking RUNNING"
                    );
                }

                return;
            }

            throw new IllegalStateException("Provider resource is not running after start: "
                    + afterStart.get().status());
        }

        throw new IllegalStateException("Unsupported provider status: " + providerResource.status());
    }
}
