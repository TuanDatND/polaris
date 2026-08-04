package com.cloud.polaris.reconcile.queue;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconcileRequestRepository extends JpaRepository<ReconcileRequest, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO reconcile_requests (
                instance_id,
                status,
                requested_generation,
                available_at,
                failure_count,
                created_at,
                updated_at
            )
            VALUES (
                :instanceId,
                'READY',
                :generation,
                :now,
                0,
                :now,
                :now
            )
            ON CONFLICT (instance_id) DO UPDATE
            SET requested_generation = GREATEST(
                    reconcile_requests.requested_generation,
                    EXCLUDED.requested_generation
                ),
                status = CASE
                    WHEN reconcile_requests.status = 'RUNNING'
                        THEN 'RUNNING'
                    ELSE 'READY'
                END,
                available_at = CASE
                    WHEN reconcile_requests.status = 'RUNNING'
                        THEN reconcile_requests.available_at
                    ELSE EXCLUDED.available_at
                END,
                failure_count = CASE
                    WHEN EXCLUDED.requested_generation
                         > reconcile_requests.requested_generation
                        THEN 0
                    ELSE reconcile_requests.failure_count
                END,
                last_error = CASE
                    WHEN EXCLUDED.requested_generation
                         > reconcile_requests.requested_generation
                        THEN NULL
                    ELSE reconcile_requests.last_error
                END,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void wake(
            @Param("instanceId") UUID instanceId,
            @Param("generation") long generation,
            @Param("now") Instant now
    );

    @Query(value = """
            SELECT *
            FROM reconcile_requests
            WHERE status = 'READY'
              AND available_at <= :now
            ORDER BY available_at, instance_id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ReconcileRequest> findReadyForUpdate(
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT *
            FROM reconcile_requests
            WHERE status = 'RUNNING'
              AND lease_expires_at <= :now
            ORDER BY lease_expires_at, instance_id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ReconcileRequest> findExpiredLeasesForUpdate(
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from ReconcileRequest request
            where request.instanceId = :instanceId
            """)
    Optional<ReconcileRequest> findByInstanceIdForUpdate(
            @Param("instanceId") UUID instanceId
    );



}
