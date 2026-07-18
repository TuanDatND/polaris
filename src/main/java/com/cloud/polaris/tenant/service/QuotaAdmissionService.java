package com.cloud.polaris.tenant.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.tenant.domain.Tenant;
import com.cloud.polaris.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotaAdmissionService {
    private final TenantRepository tenantRepository;

    @Transactional
    public void release(UUID tenantId, int cpu, int ramMb) {
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId).orElseThrow(()->new ResourceNotFoundException("Tenant not found: " + tenantId));
        tenant.release(cpu, ramMb);
    }

    @Transactional
    public void reserve(UUID tenantId, int cpu, int ramMb) {
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId).orElseThrow(()->new ResourceNotFoundException("Tenant not found: " + tenantId));
        tenant.reserve(cpu, ramMb);
    }
}
