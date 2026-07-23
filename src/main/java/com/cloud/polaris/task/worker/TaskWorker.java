package com.cloud.polaris.task.worker;

import com.cloud.polaris.common.exception.StaleTaskOwnerException;
import com.cloud.polaris.task.config.TaskProperties;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.annotation.PreDestroy;

@Component
@RequiredArgsConstructor
@Slf4j
//Strategy Pattern (Mẫu chiến lược) kết hợp với Registry (Sổ danh bạ tra cứu).
public class TaskWorker {

    private final TaskClaimService taskClaimService;
    private final TaskStateService taskStateService;
    private final TaskExecutionService taskExecutionService;
    private final List<TaskHandler> handlers;
    private final ExecutorService taskWorkerExecutor;
    private final TaskProperties taskProperties;
    private Semaphore workerSlots;
    private volatile boolean shuttingDown;

    private Map<TaskType, TaskHandler> handlerRegistry;
    private final String workerId = "worker-" + UUID.randomUUID();

    @PostConstruct
    void initializeRegistry() {
        workerSlots = new Semaphore(taskProperties.workerConcurrency());
        //toUnmodifiableMap: không thể sửa đổi sau khi tạo
        handlerRegistry = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        TaskHandler::supportedType,
                        Function.identity()
                ));
    }

    @Scheduled(fixedDelayString = "${polaris.task.poll-interval:1s}")
    public void poll() {
        if (shuttingDown) {
            return;
        }
        int reservedSlots = workerSlots.drainPermits();

        if (reservedSlots == 0) {
            return;
        }

        List<ClaimedTask> tasks;

        try {
            tasks = taskClaimService.claimTasks(reservedSlots, workerId);
        } catch (Exception exception) {
            workerSlots.release(reservedSlots);
            throw exception;
        }

        workerSlots.release(
                reservedSlots - tasks.size()
        );


        tasks.forEach(this::submitTask);
    }

    private void submitTask(ClaimedTask task) {
        try {
            taskWorkerExecutor.submit(() -> {
                try {
                    process(task);
                } finally {
                    workerSlots.release();
                }
            });
        } catch (RejectedExecutionException exception) {
            workerSlots.release();
            try {
                if (!shuttingDown) {
                    taskStateService.retry(
                            task,
                            Instant.now(),
                            "Worker executor rejected task"
                    );
                }
            }catch (Exception retryException){
                log.error(
                        "Could not requeue rejected task {}",
                        task.taskId(),
                        retryException
                );
            }

            log.error(
                    "Worker pool rejected task {}. " +
                            "Task will be recovered later.",
                    task.taskId(),
                    exception
            );
        }
    }

    @PreDestroy
    void shutdown() {
        shuttingDown = true;

        log.info("Stopping task worker...");

        taskWorkerExecutor.shutdown();

        try {
            if (!taskWorkerExecutor.awaitTermination(
                    30,
                    TimeUnit.SECONDS
            )){
                log.warn("Task workers did not finish in time. " + "Interrupting them.");
                taskWorkerExecutor.shutdownNow();
            }
        }catch (InterruptedException exception){
            Thread.currentThread().interrupt();
            taskWorkerExecutor.shutdownNow();
        }
    }

    private void process(ClaimedTask task) {
        log.debug(
                "Processing task {} on thread {}",
                task.taskId(),
                Thread.currentThread().getName()
        );
        try {
            TaskHandler handler = handlerRegistry.get(task.type());

            if (handler == null) {
                throw new IllegalStateException(
                        "No handler for task type " + task.type()
                );
            }
            handler.handle(task);
            taskStateService.markSuccess(task.taskId(), task.claimToken());
        } catch (Exception executionException) {
            handleExecutionFailure(task, executionException);
        }
    }

    private void handleExecutionFailure(
            ClaimedTask task,
            Exception executionException
    ) {
        try {
            taskExecutionService.handleFailure(
                    task,
                    executionException
            );

        } catch (StaleTaskOwnerException staleException) {
            log.warn(
                    "Stale worker ignored for task {}",
                    task.taskId()
            );

        } catch (Exception failureHandlingException) {
            log.error(
                    "Failed to handle failure for task {}. " +
                            "Task will be recovered by TaskRecovery.",
                    task.taskId(),
                    failureHandlingException
            );
        }
    }
}