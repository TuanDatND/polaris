package com.cloud.polaris.security.iam.repository;

import com.cloud.polaris.security.iam.domain.IamRole;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IamRoleRepository extends CrudRepository<IamRole, UUID> {
    List<IamRole> findByTenant_Id(UUID tenantId);

    Optional<IamRole> findByTenant_IdAndName(UUID tenantId, String name
    );
}
