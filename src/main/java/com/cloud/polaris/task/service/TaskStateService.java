package com.cloud.polaris.task.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.common.exception.StaleTaskOwnerException;
import com.cloud.polaris.event.InstanceProvisionedEvent;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.domain.TaskType;
import com.cloud.polaris.task.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskStateService {
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;


    @Transactional
    public void retry(ClaimedTask claimedTask, Instant nextAvailableAt, String error) {
        Task task = taskRepository.findByIdForUpdate(claimedTask.taskId()).orElseThrow(() -> new ResourceNotFoundException("not found task " + claimedTask.taskId()));
        if (!Objects.equals(task.getClaimToken(), claimedTask.claimToken())) {
            throw new StaleTaskOwnerException(task.getId().toString());
        }
        task.retry(nextAvailableAt, error);
    }

    @Transactional
    public void markSuccess(UUID taskId, UUID claimToken) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Task not found: " + taskId
                        )
                );

        if (!Objects.equals(task.getClaimToken(), claimToken)) {
            throw new StaleTaskOwnerException(
                    task.getId().toString()
            );
        }

        task.markSuccess();

        if (task.getType() == TaskType.CREATE_INSTANCE) {
            eventPublisher.publishEvent(
                    new InstanceProvisionedEvent(
                            task.getInstance().getId(),
                            task.getInstance().getGeneration()
                    )
            );
        }
    }

    @Transactional
    public void markFailed(ClaimedTask claimedTask, String error) {
        Task task = taskRepository.findByIdForUpdate(claimedTask.taskId()).orElseThrow(() -> new ResourceNotFoundException("Task not found: " + claimedTask.taskId()));

        if (claimedTask.claimToken() == null
                || !Objects.equals(task.getClaimToken(), claimedTask.claimToken())) {
            throw new StaleTaskOwnerException(task.getId().toString());
        }
        task.markFailed(error);
    }

    @Transactional
    public void assertOwnership(ClaimedTask claimedTask) {
        Task task = taskRepository.findByIdForUpdate(claimedTask.taskId()).orElseThrow(() -> new ResourceNotFoundException("not found task " + claimedTask.taskId()));

        if (claimedTask.claimToken() == null || !Objects.equals(task.getClaimToken(), claimedTask.claimToken())) {
            throw new StaleTaskOwnerException(task.getId().toString());
        }
    }


}
