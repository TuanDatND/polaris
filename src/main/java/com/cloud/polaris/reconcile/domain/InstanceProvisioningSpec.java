package com.cloud.polaris.reconcile.domain;

import java.util.UUID;

public record InstanceProvisioningSpec(
        UUID instanceId,
        UUID tenantId,
        String imageName,
        int cpuAllocated,
        int ramMb
) {
}
