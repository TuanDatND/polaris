package com.cloud.polaris.task.service.failure;

import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import com.cloud.polaris.task.service.TaskFailureCompensationService;
import com.cloud.polaris.task.service.TaskStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartTaskFailureHandler implements TaskFailureHandler {

    private final TaskStateService taskStateService;

    @Override
    public TaskType supportedType() {
        return TaskType.START_INSTANCE;
    }

    @Override
    public void handle(ClaimedTask task, Exception exception) {
        taskStateService.markFailed(task, exception.getMessage());
    }
}
