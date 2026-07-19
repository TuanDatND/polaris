package com.cloud.polaris.task.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.service.InstanceCompensationService;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskFailureCompensationService {

    private final ComputeProvider computeProvider;
    private final InstanceCompensationService instanceCompensationService;

    public void compensate(UUID taskId, UUID tenantId, UUID instanceId, String reason) {
        Optional<ProviderResource> resource = computeProvider.findByInstanceId(instanceId);

        boolean resourceAbsent = resource.isEmpty();

        if (resource.isPresent()) {
            try {
                computeProvider.delete(resource.get().providerResourceId());
                resourceAbsent = true;
            } catch (Exception cleanupException) {
                // Không được giả định container đã biến mất
                resourceAbsent = false;
            }
        }
        instanceCompensationService.finalizeFailure(taskId, tenantId, instanceId, reason, resourceAbsent);
    }
}
