package com.cloud.polaris.task.service;

import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import com.cloud.polaris.task.service.failure.TaskFailureHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskExecutionService {

    private final TaskStateService taskStateService;
    private final Map<TaskType, TaskFailureHandler> failureHandlers;

    // Strategy Registry
    public TaskExecutionService(TaskStateService taskStateService, List<TaskFailureHandler> handlers) {
         this.taskStateService = taskStateService;

         this.failureHandlers = handlers.stream()
                 .collect(Collectors.toUnmodifiableMap(
                         TaskFailureHandler::supportedType,
                         Function.identity()
                 ));
    }

    public void handleFailure(ClaimedTask claimedTask, Exception exception) {
        if (claimedTask.attempts() < claimedTask.maxAttempts()) {
            taskStateService.retry(claimedTask, Instant.now().plusSeconds(1L << claimedTask.attempts()), exception.getMessage());
            return;
        }

        TaskFailureHandler handler =failureHandlers.get(claimedTask.type());

        if (handler == null) {
            throw new IllegalStateException("No handler for " + claimedTask.type() + " for " + exception.getMessage());
        }

        handler.handle(claimedTask, exception);
    }
}
