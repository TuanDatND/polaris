package com.cloud.polaris.task.handler;

import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import org.springframework.stereotype.Component;

@Component
public class StartInstanceHandler implements TaskHandler {

    @Override
    public TaskType supportedType() {
        return TaskType.START_INSTANCE;
    }

    @Override
    public void handle(ClaimedTask task) {

    }
}
