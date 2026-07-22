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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StopInstanceHandler implements TaskHandler {

    private final ComputeProvider computeProvider;
    private final InstanceLifecycleService instanceLifecycleService;
    private final InstanceRepository instanceRepository;

    @Override
    public TaskType supportedType() {
        return TaskType.STOP_INSTANCE;
    }

    @Override
    public void handle(ClaimedTask claimedTask) {
        Instance instance = instanceRepository.findById(claimedTask.instanceId()).orElseThrow(() -> new ResourceNotFoundException("Instance not found "));

        if (instance.getDesiredState() != DesiredState.STOPPED || instance.getCurrentState() == CurrentState.STOPPED) {
            return;
        }

        Optional<ProviderResource> resource = computeProvider.findByInstanceId(claimedTask.instanceId());

        if (resource.isEmpty()) {
            instanceLifecycleService.completeStop(
                    claimedTask,
                    true
            );
            return;
        }

        if (resource.get().status() == ProviderResourceStatus.STOPPED
                || resource.get().status() == ProviderResourceStatus.CREATED) {

            instanceLifecycleService.completeStop(claimedTask, false);
            return;
        }

        if (resource.get().status() == ProviderResourceStatus.UNKNOWN) {
            instanceLifecycleService.ensureStopping(claimedTask);

            throw new IllegalStateException(
                    "Provider resource has unknown status"
            );
        }

        if (resource.get().status() != ProviderResourceStatus.RUNNING) {
            throw new IllegalStateException(
                    "Cannot stop resource with status "
                            + resource.get().status()
            );
        }

        instanceLifecycleService.ensureStopping(claimedTask);
        computeProvider.stop(resource.get().providerResourceId());

        Optional<ProviderResource> afterStop =
                computeProvider.findByInstanceId(
                        claimedTask.instanceId()
                );

        if (afterStop.isEmpty()) {
            instanceLifecycleService.completeStop(
                    claimedTask,
                    true
            );
            return;
        }

        if (afterStop.get().status() == ProviderResourceStatus.STOPPED
                || afterStop.get().status() == ProviderResourceStatus.CREATED) {
            instanceLifecycleService.completeStop(claimedTask, false);
            return;
        }

        if (afterStop.get().status() == ProviderResourceStatus.RUNNING) {
            throw new IllegalStateException(
                    "Resource is still running after stop"
            );
        }

        if (afterStop.get().status() == ProviderResourceStatus.UNKNOWN) {
            instanceLifecycleService.ensureStopping(claimedTask);
            throw new IllegalStateException(
                    "Provider resource has unknown status after stop: "
                            + afterStop.get().status()
            );
        }
    }
}
