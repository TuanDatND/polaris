package com.cloud.polaris.task.handler;

import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;

public interface TaskHandler {
    TaskType supportedType();

    void handle(ClaimedTask task);
}
