package com.cloud.polaris.security.iam.repository;

import com.cloud.polaris.security.iam.domain.composite.IamRolePolicy;
import com.cloud.polaris.security.iam.domain.id.RolePolicyId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IamRolePolicyRepository extends JpaRepository<IamRolePolicy, RolePolicyId> {

    List<IamRolePolicy> findByRole_Id(UUID roleId);

    List<IamRolePolicy> findByPolicy_Id(UUID policyId);
}
