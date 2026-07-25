package com.cloud.polaris.security.iam.repository;

import com.cloud.polaris.security.iam.domain.entity.IamPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IamPolicyRepository extends JpaRepository<IamPolicy, UUID> {
    List<IamPolicy> findByTenant_Id(UUID tenantId);

    Optional<IamPolicy> findByTenant_IdAndName(
            UUID tenantId,
            String name
    );

    Optional<IamPolicy> findByTenantIsNullAndName(
            String name
    );
}
