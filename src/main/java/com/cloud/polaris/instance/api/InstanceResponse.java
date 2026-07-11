package com.cloud.polaris.instance.api;

import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.instance.domain.Instance;

import java.time.Instant;
import java.util.UUID;

public record InstanceResponse(
        UUID id,
        UUID tenantId,
        String name,
        String imageName,
        Integer cpuAllocated,
        Integer ramMb,
        DesiredState desiredState,
        CurrentState currentState,
        String containerId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static InstanceResponse from(Instance instance) {
        return new InstanceResponse(
                instance.getId(),
                instance.getTenant().getId(),
                instance.getName(),
                instance.getImageName(),
                instance.getCpuAllocated(),
                instance.getRamMb(),
                instance.getDesiredState(),
                instance.getCurrentState(),
                instance.getContainerId(),
                instance.getFailureReason(),
                instance.getCreatedAt(),
                instance.getUpdatedAt()
        );
    }
}
