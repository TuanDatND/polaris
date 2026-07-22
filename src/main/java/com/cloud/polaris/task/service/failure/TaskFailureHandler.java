package com.cloud.polaris.task.service.failure;

import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;

public interface TaskFailureHandler {

    TaskType supportedType();

    void handle(ClaimedTask task, Exception exception);

}
