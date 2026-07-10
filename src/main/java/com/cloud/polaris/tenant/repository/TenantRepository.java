package com.cloud.polaris.tenant.repository;

import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.tenant.domain.Tenant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface  TenantRepository extends JpaRepository<Tenant, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tenant t WHERE t.id = :id")
    Optional<Tenant> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByUsername(String username);
}
