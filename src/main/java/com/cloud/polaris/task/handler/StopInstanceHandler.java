package com.cloud.polaris.task.handler;

import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import org.springframework.stereotype.Component;

@Component
public class StopInstanceHandler implements TaskHandler {

    @Override
    public TaskType supportedType() {
        return TaskType.STOP_INSTANCE;
    }

    @Override
    public void handle(ClaimedTask task) {

    }
}
