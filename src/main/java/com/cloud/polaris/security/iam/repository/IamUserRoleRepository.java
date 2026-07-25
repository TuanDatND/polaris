package com.cloud.polaris.security.iam.repository;

import com.cloud.polaris.security.iam.domain.composite.IamUserRole;
import com.cloud.polaris.security.iam.domain.id.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IamUserRoleRepository extends JpaRepository<IamUserRole, UserRoleId> {

    List<IamUserRole> findByUser_Id(UUID userId);

    List<IamUserRole> findByRole_Id(UUID roleId);
}
