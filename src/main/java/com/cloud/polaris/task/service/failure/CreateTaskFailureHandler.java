package com.cloud.polaris.task.service.failure;

import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import com.cloud.polaris.task.service.TaskFailureCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateTaskFailureHandler implements TaskFailureHandler {

    private final TaskFailureCompensationService taskFailureCompensationService;

    @Override
    public TaskType supportedType() {
        return TaskType.CREATE_INSTANCE;
    }

    @Override
    public void handle(ClaimedTask task, Exception exception) {
        taskFailureCompensationService.compensate(task, exception.getMessage());
    }
}
