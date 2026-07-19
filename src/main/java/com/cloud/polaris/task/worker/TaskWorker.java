package com.cloud.polaris.task.worker;

import com.cloud.polaris.common.exception.StaleTaskOwnerException;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import com.cloud.polaris.task.handler.TaskHandler;
import com.cloud.polaris.task.service.TaskClaimService;
import com.cloud.polaris.task.service.TaskExecutionService;
import com.cloud.polaris.task.service.TaskStateService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
//Strategy Pattern (Mẫu chiến lược) kết hợp với Registry (Sổ danh bạ tra cứu).
public class TaskWorker {

    private final TaskClaimService taskClaimService;
    private final TaskStateService taskStateService;
    private final TaskExecutionService taskExecutionService;
    private final List<TaskHandler> handlers;

    private Map<TaskType, TaskHandler> handlerRegistry;
    private final String workerId = "worker-" + UUID.randomUUID();

    @PostConstruct
    void initializeRegistry() {
        //toUnmodifiableMap: không thể sửa đổi sau khi tạo
        handlerRegistry = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        TaskHandler::supportedType,
                        Function.identity()
                ));
    }

    @Scheduled(fixedDelay = 1000)
    public void poll(){
        List<ClaimedTask> tasks = taskClaimService.claimTasks(10, workerId);

        tasks.forEach(this::process);
    }

    private void process(ClaimedTask task) {
        try{
            TaskHandler handler = handlerRegistry.get(task.type());

            if (handler == null) {
                throw new IllegalStateException(
                        "No handler for task type " + task.type()
                );
            }
            handler.handle(task);
            taskStateService.markSuccess(task.taskId(), task.claimToken());
        } catch (StaleTaskOwnerException exception) {
            log.warn("Stale worker ignored for task {}", task.taskId());
        } catch (Exception exception) {
            taskExecutionService.handleFailure(task, exception);
        }
    }
}