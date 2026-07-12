package com.cloud.polaris.instance.service;

import com.cloud.polaris.common.exception.DuplicateResourceException;
import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.api.CreateInstanceRequest;
import com.cloud.polaris.instance.api.InstanceResponse;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.repository.TaskRepository;
import com.cloud.polaris.tenant.domain.Tenant;
import com.cloud.polaris.tenant.repository.TenantRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstanceService {

    private final InstanceRepository instanceRepository;
    private final TenantRepository tenantRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public InstanceResponse createInstance(UUID tenantId, CreateInstanceRequest request) {

        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));

        if (instanceRepository.existsByTenant_IdAndNameAndCurrentStateNot(tenantId, request.name(),CurrentState.DELETED)) {
            throw new DuplicateResourceException("Instance name already exists in tenant: " + request.name());
        }

        tenant.reserve(request.cpu(), request.ramMb());
        Instance instance = instanceRepository.save(
                Instance.createPending(
                        tenant,
                        request.name(),
                        request.imageName(),
                        request.cpu(),
                        request.ramMb()));

        ObjectNode payload = JsonNodeFactory.instance.objectNode()
                .put("name", instance.getName())
                .put("imageName", instance.getImageName())
                .put("cpuAllocated", instance.getCpuAllocated())
                .put("ramMb", instance.getRamMb());

        Task task = Task.createInstanceTask(tenant, instance, payload);
        taskRepository.save(task);
        return InstanceResponse.from(instance);
    }

    @Transactional(readOnly = true)
    public InstanceResponse getInstance(UUID tenantId, UUID instanceId) {
        Instance instance = instanceRepository.findByIdAndTenant_Id(instanceId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));
        return InstanceResponse.from(instance);
    }
}
