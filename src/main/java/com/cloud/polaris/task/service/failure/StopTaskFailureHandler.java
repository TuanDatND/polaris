package com.cloud.polaris.task.service.failure;

import com.cloud.polaris.instance.service.InstanceLifecycleService;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.provider.ProviderResourceStatus;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import com.cloud.polaris.task.service.TaskStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StopTaskFailureHandler implements TaskFailureHandler {

    private final TaskStateService taskStateService;
    private final ComputeProvider computeProvider;
    private final InstanceLifecycleService instanceLifecycleService;

    @Override
    public TaskType supportedType() {
        return TaskType.STOP_INSTANCE;
    }

    @Override
    public void handle(ClaimedTask task, Exception exception) {

        Optional<ProviderResource> resource = computeProvider.findByInstanceId(task.instanceId());

        if (resource.isEmpty()) {
            instanceLifecycleService.completeStop(task, true);
            taskStateService.markSuccess(task.taskId(), task.claimToken());
            return;
        }

        if (resource.get().status() == ProviderResourceStatus.STOPPED
                || resource.get().status() == ProviderResourceStatus.CREATED) {

            instanceLifecycleService.completeStop(task, false);
            taskStateService.markSuccess(task.taskId(), task.claimToken());
            return;
        }

        if (resource.get().status() == ProviderResourceStatus.RUNNING){
            instanceLifecycleService.markStopFailed(task, exception.getMessage());
        }

        if (resource.get().status() == ProviderResourceStatus.UNKNOWN) {
            instanceLifecycleService.ensureStopping(task);
        }

        taskStateService.markFailed(
                task,
                exception.getMessage()
        );
    }
}
