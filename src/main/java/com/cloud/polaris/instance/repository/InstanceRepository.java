package com.cloud.polaris.instance.repository;

import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.instance.domain.Instance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstanceRepository extends JpaRepository<Instance, UUID> {

    boolean existsByTenant_IdAndNameAndCurrentStateNot(UUID tenantId,
                                                       String name,
                                                       CurrentState currentState);

    Optional<Instance> findByIdAndTenant_Id(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Instance i where i.id = :id")
    Optional<Instance> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Instance i where i.id = :instanceId and i.tenant.id = :tenantId")
    Optional<Instance> findByIdAndTenantIdForUpdate(
            @Param("instanceId") UUID instanceId,
            @Param("tenantId") UUID tenantId
    );

    @Query("""
                select i.id
                from Instance i
                where i.currentState = :state
                  and i.desiredState = 'RUNNING'
                  and i.quotaReleased = false
            """)
    List<UUID> findFailedInstanceIdsForCleanup(
            @Param("state") CurrentState state
    );

    @Query("""
            select i.id from Instance i
            where i.currentState = :currentState
              and i.desiredState = :desiredState order by i.updatedAt""")
    List<UUID> findInstanceIdsForStopReconciliation(
            @Param("currentState") CurrentState currentState,
            @Param("desiredState") DesiredState desiredState,
            Pageable pageable
    );

    @Query("""
        select i.id
        from Instance i
        where i.currentState = :currentState
          and i.desiredState = :desiredState
        order by i.updatedAt
        """)
    List<UUID> findInstanceIdsForStartReconciliation(
            @Param("currentState") CurrentState currentState,
            @Param("desiredState") DesiredState desiredState,
            Pageable pageable
    );

    @Query("""
        select i.id
        from Instance i
        where i.currentState = :currentState
          and i.desiredState = :desiredState
        order by i.updatedAt
        """)
    List<UUID> findInstanceIdsForDeleteReconciliation(
            @Param("currentState") CurrentState currentState,
            @Param("desiredState") DesiredState desiredState,
            Pageable pageable
    );
}

