package com.cloud.polaris.security.iam.repository;

import com.cloud.polaris.security.iam.domain.entity.IamRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IamRoleRepository extends JpaRepository<IamRole, UUID> {
    List<IamRole> findByTenant_Id(UUID tenantId);

    Optional<IamRole> findByTenantIsNullAndName(String name
    );
}
