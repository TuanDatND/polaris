package com.cloud.polaris.instance.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.repository.TaskRepository;
import com.cloud.polaris.tenant.domain.Tenant;
import com.cloud.polaris.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstanceCompensationService {
    private final InstanceRepository instanceRepository;
    private final TenantRepository tenantRepository;
    private final TaskRepository taskRepository;
    private final InstanceLifecycleService instanceLifecycleService;

    @Transactional
    public void finalizeFailure(UUID taskId, UUID tenantId, UUID instanceId, String reason, boolean resourceAbsent) {
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Task task = taskRepository.findByIdForUpdate(taskId).orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        Instance instance = instanceRepository.findByIdAndTenantIdForUpdate(instanceId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Instance not found"));

        if (task.getAttempts() >= task.getMaxAttempts()) {
            task.markFailed(reason);
        }

        if (instance.getCurrentState() != CurrentState.FAILED) {
            instanceLifecycleService.markFailed(instance, reason);
        }

        if (resourceAbsent && instance.releaseQuota()) {
            tenant.release(
                    instance.getCpuAllocated(),
                    instance.getRamMb()
            );
        }
    }

    @Transactional
    public void releaseQuotaIfCleanupCompleted(UUID instanceId) {
        Instance snapshot = instanceRepository.findById(instanceId)
                .orElseThrow();

        UUID tenantId = snapshot.getTenant().getId();

        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow();

        Instance instance = instanceRepository
                .findByIdAndTenantIdForUpdate(instanceId, tenantId)
                .orElseThrow();

        if (instance.getCurrentState() != CurrentState.FAILED) {
            return;
        }

        if (instance.releaseQuota()) {
            tenant.release(
                    instance.getCpuAllocated(),
                    instance.getRamMb()
            );
        }
    }

}
