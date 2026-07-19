package com.cloud.polaris.task.service;

import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskClaimService {
    private final TaskRepository taskRepository;

    @Transactional
    public List<ClaimedTask> claimTasks(int limit, String workerId) {
        //warning lazy fetch don't have enough data
        List<Task> tasks = taskRepository.findQueuedTasksForUpdate(limit);
        Instant now = Instant.now();

        tasks.forEach(task -> task.claim(workerId, UUID.randomUUID(),now));
        return tasks.stream()
                .map(ClaimedTask::from)
                .toList();
    }

    @Transactional
    public int recoverExpiredTasks(Duration timeout, int limit) {
        Instant cutoff = Instant.now().minus(timeout);

        List<Task> tasks =
                taskRepository.findStaleRunningTasksForUpdate(cutoff, limit);

        Instant now = Instant.now();
        tasks.forEach(task -> task.recover(now));

        return tasks.size();
    }
}
