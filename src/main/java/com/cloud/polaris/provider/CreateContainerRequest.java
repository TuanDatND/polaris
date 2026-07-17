package com.cloud.polaris.provider;

import com.cloud.polaris.task.domain.Task;

import java.util.Map;
import java.util.UUID;

public record CreateContainerRequest(
        UUID instanceId,
        UUID tenantId,
        String name,
        String imageName,
        int cpuAllocated,
        int ramMb,
        Map<String, String> labels
) {
}
