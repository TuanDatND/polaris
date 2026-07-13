package com.cloud.polaris.task.service;

import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;

    @Transactional
    public List<ClaimedTask> claimTasks(int limit, String workerId) {
        List<Task> tasks = taskRepository.findQueuedTasksForUpdate(limit);
        Instant now = Instant.now();

        tasks.forEach(task -> task.claim(workerId, now));
        return tasks.stream()
                .map(ClaimedTask::from)
                .toList();
    }
}
