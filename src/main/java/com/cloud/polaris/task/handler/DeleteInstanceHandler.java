package com.cloud.polaris.task.handler;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.instance.service.InstanceCompensationService;
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
public class DeleteInstanceHandler implements TaskHandler {

    private final InstanceRepository instanceRepository;
    private final InstanceLifecycleService instanceLifecycleService;
    private final ComputeProvider computeProvider;
    private final InstanceCompensationService cleanupService;

    @Override
    public TaskType supportedType() {
        return TaskType.DELETE_INSTANCE;
    }

    @Override
    public void handle(ClaimedTask claimedTask) {
        Instance instance = instanceRepository.findById(claimedTask.instanceId()).orElseThrow(() -> new ResourceNotFoundException("Instance not found "));

        if (instance.getDesiredState() != DesiredState.DELETED) {
            return;
        }

        instanceLifecycleService.ensureDeleting(claimedTask);

        Optional<ProviderResource> resource =
                computeProvider.findByInstanceId(
                        claimedTask.instanceId()
                );
        if (resource.isEmpty()){
            finalizeDelete(claimedTask);
            return;
        }

        if (resource.get().status()
                == ProviderResourceStatus.UNKNOWN) {
            throw new IllegalStateException(
                    "Provider resource status is unknown"
            );
        }

        computeProvider.delete(resource.get().providerResourceId());

        Optional<ProviderResource> afterDelete =
                computeProvider.findByInstanceId(
                        claimedTask.instanceId()
                );
        if (afterDelete.isEmpty()) {
            finalizeDelete(claimedTask);
            return;
        }
        throw new IllegalStateException("Provider resource still exists after delete");
    }

    private void finalizeDelete(ClaimedTask task) {
        instanceLifecycleService.completeDelete(task, true);

        cleanupService.releaseQuotaIfCleanupCompleted(
                task.instanceId()
        );
    }
}
