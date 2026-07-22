package com.cloud.polaris.task.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.common.exception.StaleTaskOwnerException;
import com.cloud.polaris.instance.service.InstanceCompensationService;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskFailureCompensationService {

    private final ComputeProvider computeProvider;
    private final InstanceCompensationService instanceCompensationService;
    private final TaskStateService taskStateService;

    public void compensate(ClaimedTask claimedTask, String reason) {
        Optional<ProviderResource> resource = computeProvider.findByInstanceId(claimedTask.instanceId());

        boolean resourceAbsent = resource.isEmpty();

        if (resource.isPresent()) {
            taskStateService.assertOwnership(claimedTask);
            try {
                computeProvider.delete(resource.get().providerResourceId());
                resourceAbsent = true;
            } catch (Exception cleanupException) {
                log.error(
                        "Failed to cleanup provider resource {} for task {}",
                        resource.get().providerResourceId(),
                        claimedTask.taskId(),
                        cleanupException
                );
            }
        }
        instanceCompensationService.finalizeFailure(claimedTask, reason, resourceAbsent);
    }
}
