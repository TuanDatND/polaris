package com.cloud.polaris.tenant.service;

import com.cloud.polaris.common.exception.DuplicateResourceException;
import com.cloud.polaris.tenant.api.CreateTenantRequest;
import com.cloud.polaris.tenant.api.TenantResponse;
import com.cloud.polaris.tenant.domain.Tenant;
import com.cloud.polaris.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final TenantRepository tenantRepository;

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException(request.username());
        }

        Tenant tenant = Tenant.create(request.username(), request.quotaCpu(), request.quotaRamMb(), request.quotaInstanceCount());

        return TenantResponse.from(tenantRepository.save(tenant));
    }
}
