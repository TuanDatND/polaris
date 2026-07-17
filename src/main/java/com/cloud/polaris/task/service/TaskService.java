package com.cloud.polaris.task.service;

import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;

    @Transactional
    public List<ClaimedTask> claimTasks(int limit, String workerId) {
        //warning lazy fetch don't have enough data
        List<Task> tasks = taskRepository.findQueuedTasksForUpdate(limit);
        Instant now = Instant.now();

        tasks.forEach(task -> task.claim(workerId, now));
        return tasks.stream()
                .map(ClaimedTask::from)
                .toList();
    }

    @Transactional
    public void markSuccess(UUID taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();

        task.markSuccess();
    }

    @Transactional
    public void handleFailure(UUID taskId, Exception exception) {
        Task task = taskRepository.findById(taskId).orElseThrow();

        if (task.getAttempts()>=task.getMaxAttempts()) {
            task.markFailed(exception.getMessage());
            return;
        }

        long delaySeconds = 1L << task.getAttempts();

        task.retry(
                Instant.now().plusSeconds(delaySeconds),
                exception.getMessage()
        );
    }
}
