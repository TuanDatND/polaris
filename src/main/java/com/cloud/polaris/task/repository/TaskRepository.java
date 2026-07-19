package com.cloud.polaris.task.repository;

import com.cloud.polaris.task.domain.Task;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query(value = """
            SELECT *
            FROM tasks
            WHERE status = 'QUEUED'
              AND available_at <= now()
              AND attempts < max_attempts
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED 
            """, nativeQuery = true)
    List<Task> findQueuedTasksForUpdate(@Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT *
            FROM tasks
            WHERE status = 'RUNNING'
              AND locked_at IS NOT NULL
              AND locked_at < :cutoff
            ORDER BY locked_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Task> findStaleRunningTasksForUpdate(@Param("cutoff")Instant cutoff, @Param("limit") int limit);

}
