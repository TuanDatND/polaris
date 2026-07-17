package com.cloud.polaris.task.repository;

import com.cloud.polaris.task.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

}
