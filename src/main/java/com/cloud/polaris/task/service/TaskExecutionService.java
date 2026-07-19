package com.cloud.polaris.task.service;

import com.cloud.polaris.task.domain.ClaimedTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TaskExecutionService {
    private final TaskStateService taskStateService;
    private final TaskFailureCompensationService failureCompensationService;

    public void handleFailure(ClaimedTask claimedTask, Exception exception) {
        if (claimedTask.attempts() < claimedTask.maxAttempts()) {
            taskStateService.retry(claimedTask, Instant.now().plusSeconds(1L << claimedTask.attempts()), exception.getMessage());
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
