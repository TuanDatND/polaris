package com.cloud.polaris.instance.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.api.InstanceResponse;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstanceQueryService {
    private final InstanceRepository instanceRepository;

    @Transactional(readOnly = true)
    public InstanceResponse getInstance(UUID tenantId, UUID instanceId) {
        Instance instance = instanceRepository.findByIdAndTenant_Id(instanceId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));
        return InstanceResponse.from(instance);
    }

}
