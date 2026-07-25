package com.cloud.polaris.security.iam.repository;

import com.cloud.polaris.security.iam.domain.id.MembershipId;
import com.cloud.polaris.security.iam.domain.enums.MembershipStatus;
import com.cloud.polaris.security.iam.domain.entity.TenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, MembershipId> {

    boolean existsByUser_IdAndTenant_IdAndStatus(UUID userId, UUID tenantId, MembershipStatus status);

    List<TenantMembership> findByUser_IdAndStatus(UUID userId, MembershipStatus status);
}
