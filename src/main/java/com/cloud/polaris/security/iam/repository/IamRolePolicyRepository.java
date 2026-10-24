package com.cloud.polaris.security.iam.repository;

import com.cloud.polaris.security.iam.domain.IamRolePolicy;
import com.cloud.polaris.security.iam.domain.id.RolePolicyId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IamRolePolicyRepository extends JpaRepository<IamRolePolicy, RolePolicyId> {

    List<IamRolePolicy> findByRole_Id(UUID roleId);

    List<IamRolePolicy> findByPolicy_Id(UUID policyId);
}
