package com.cloud.polaris.task.service.repair;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.domain.TaskStatus;
import com.cloud.polaris.task.domain.TaskType;
import com.cloud.polaris.task.repository.TaskRepository;
import com.cloud.polaris.tenant.domain.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RepairService {

    private final InstanceRepository instanceRepository;
    private final TaskRepository taskRepository;


    @Transactional
    public void reconcileOne(UUID instanceId) {
        Instance instance = instanceRepository.findByIdForUpdate(instanceId).orElseThrow(() -> new ResourceNotFoundException(
                "Instance not found: " + instanceId
        ));

        TaskType taskType = determineTaskType(instance);

        if (taskType == null) {
            return;
        }

        boolean activeTaskExists =
                taskRepository.existsByInstance_IdAndStatusIn(
                        instance.getId(),
                        Set.of(TaskStatus.QUEUED, TaskStatus.RUNNING)
                );

        if (activeTaskExists) {
            return;
        }

        String idempotencyKey = buildIdempotencyKey(instance, taskType);

        Task task = createCorrectiveTask(
                instance,
                taskType,
                idempotencyKey
        );

        taskRepository.save(task);
    }

    private Task createCorrectiveTask(Instance instance, TaskType taskType, String idempotencyKey) {
        Tenant tenant = instance.getTenant();

        return switch (taskType) {
            case START_INSTANCE -> Task.startInstanceTask(tenant, instance, idempotencyKey);

            case STOP_INSTANCE -> Task.stopInstanceTask(tenant, instance, null, idempotencyKey);

            case DELETE_INSTANCE -> Task.deleteInstanceTask(tenant, instance, idempotencyKey);

            default -> throw new IllegalStateException("Unsupported corrective task type: " + taskType);
        };
    }

    private TaskType determineTaskType(Instance instance) {
        return switch (instance.getDesiredState()) {
            case RUNNING -> {
                if (instance.getCurrentState() == CurrentState.STOPPED) {
                    yield TaskType.START_INSTANCE;
                }
                yield null;
            }

            case STOPPED -> {
                if (instance.getCurrentState() == CurrentState.RUNNING) {
                    yield TaskType.STOP_INSTANCE;
                }
                yield null;
            }

            case DELETED -> {
                if (instance.getCurrentState() == CurrentState.STOPPED) {
                    yield TaskType.DELETE_INSTANCE;
                }
                yield null;
            }
        };
    }

    private String buildIdempotencyKey(
            Instance instance,
            TaskType taskType
    ) {
        return "reconcile:"
                + taskType
                + ":"
                + instance.getId()
                + ":"
                + instance.getVersion();
    }
}
