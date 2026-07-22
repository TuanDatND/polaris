package com.cloud.polaris.task.worker;

import com.cloud.polaris.task.config.TaskProperties;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import com.cloud.polaris.task.handler.TaskHandler;
import com.cloud.polaris.task.service.TaskClaimService;
import com.cloud.polaris.task.service.TaskExecutionService;
import com.cloud.polaris.task.service.TaskStateService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskWorkerTest {

    @Mock
    TaskClaimService taskClaimService;
    @Mock
    TaskStateService taskStateService;
    @Mock
    TaskExecutionService taskExecutionService;
    @Mock
    TaskHandler taskHandler;

    private ExecutorService executor;
    private TaskWorker worker;

    @BeforeEach
    void setUp() {
        TaskProperties properties = new TaskProperties(
                Duration.ofSeconds(1),
                1,
                Duration.ofMinutes(5),
                100
        );
        executor = Executors.newSingleThreadExecutor();
        when(taskHandler.supportedType()).thenReturn(TaskType.CREATE_INSTANCE);

        worker = new TaskWorker(
                taskClaimService,
                taskStateService,
                taskExecutionService,
                List.of(taskHandler),
                executor,
                properties
        );
        worker.initializeRegistry();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void should_ExecuteClaimedTaskAndMarkSuccess_when_PollFindsTask()
            throws Exception {
        ClaimedTask task = claimedCreateTask();
        CountDownLatch handled = new CountDownLatch(1);

        when(taskClaimService.claimTasks(eq(1), anyString()))
                .thenReturn(List.of(task));
        doAnswer(invocation -> {
            handled.countDown();
            return null;
        }).when(taskHandler).handle(task);

        worker.poll();

        assertThat(handled.await(5, TimeUnit.SECONDS)).isTrue();
        verify(taskHandler, timeout(5_000)).handle(task);
        verify(taskStateService, timeout(5_000))
                .markSuccess(task.taskId(), task.claimToken());
    }

    @Test
    void should_ClaimOnlyAvailableWorkerSlots_when_Polling()
            throws Exception {
        when(taskClaimService.claimTasks(eq(1), anyString()))
                .thenReturn(List.of());

        worker.poll();

        verify(taskClaimService).claimTasks(eq(1), anyString());
    }

    private ClaimedTask claimedCreateTask() {
        return new ClaimedTask(
                UUID.randomUUID(),
                TaskType.CREATE_INSTANCE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JsonNodeFactory.instance.objectNode(),
                1,
                5,
                UUID.randomUUID()
        );
    }
}
