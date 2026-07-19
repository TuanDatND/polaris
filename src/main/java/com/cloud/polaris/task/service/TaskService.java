package com.cloud.polaris.task.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.service.InstanceCompensationService;
import com.cloud.polaris.instance.service.InstanceService;
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
    private final TaskFailureCompensationService failureCompensationService;
    private final TaskStateService taskStateService;

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



    public void handleFailure(ClaimedTask claimedTask, Exception exception) {
        if (claimedTask.attempts() < claimedTask.maxAttempts()) {
            taskStateService.retry(claimedTask, Instant.now().plusSeconds(  1L << claimedTask.attempts()), exception.getMessage());
            return;
        }
        failureCompensationService.compensate(
                claimedTask.taskId(),
                claimedTask.tenantId(),
                claimedTask.instanceId(),
                exception.getMessage()
        );
    }
}
