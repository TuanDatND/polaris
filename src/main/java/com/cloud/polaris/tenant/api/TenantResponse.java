package com.cloud.polaris.tenant.api;

import com.cloud.polaris.tenant.domain.Tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String username,
        Integer quotaCpu,
        Integer quotaRamMb,
        Integer quotaInstanceCount,
        Integer allocatedCpu,
        Integer allocatedRamMb,
        Integer allocatedInstanceCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getUsername(),
                tenant.getQuotaCpu(),
                tenant.getQuotaRamMb(),
                tenant.getQuotaInstanceCount(),
                tenant.getAllocatedCpu(),
                tenant.getAllocatedRamMb(),
                tenant.getAllocatedInstanceCount(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
