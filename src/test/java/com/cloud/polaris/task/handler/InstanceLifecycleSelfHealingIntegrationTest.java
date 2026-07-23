package com.cloud.polaris.task.handler;

import com.cloud.polaris.instance.api.CreateInstanceRequest;
import com.cloud.polaris.instance.api.InstanceResponse;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.instance.service.InstanceCommandService;
import com.cloud.polaris.instance.service.InstanceLifecycleService;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.provider.ProviderResourceStatus;
import com.cloud.polaris.reconcile.DeleteReconciler;
import com.cloud.polaris.reconcile.InstanceReconciler;
import com.cloud.polaris.reconcile.StartReconciler;
import com.cloud.polaris.reconcile.StopReconciler;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.domain.TaskStatus;
import com.cloud.polaris.task.domain.TaskType;
import com.cloud.polaris.task.repository.TaskRepository;
import com.cloud.polaris.task.service.TaskClaimService;
import com.cloud.polaris.task.service.TaskExecutionService;
import com.cloud.polaris.task.service.TaskStateService;
import com.cloud.polaris.task.worker.TaskRecovery;
import com.cloud.polaris.task.worker.TaskWorker;
import com.cloud.polaris.tenant.domain.Tenant;
import com.cloud.polaris.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
class InstanceLifecycleSelfHealingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    InstanceCommandService commandService;
    @Autowired
    InstanceLifecycleService lifecycleService;
    @Autowired
    CreateInstanceHandler createHandler;
    @Autowired
    StartInstanceHandler startHandler;
    @Autowired
    StopInstanceHandler stopHandler;
    @Autowired
    DeleteInstanceHandler deleteHandler;
    @Autowired
    TaskClaimService taskClaimService;
    @Autowired
    TaskStateService taskStateService;
    @Autowired
    TaskExecutionService taskExecutionService;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    InstanceRepository instanceRepository;
    @Autowired
    TaskRepository taskRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    ComputeProvider computeProvider;
    @MockitoBean
    TaskWorker taskWorker;
    @MockitoBean
    TaskRecovery taskRecovery;
    @MockitoBean
    InstanceReconciler instanceReconciler;
    @MockitoBean
    StopReconciler stopReconciler;
    @MockitoBean
    StartReconciler startReconciler;
    @MockitoBean
    DeleteReconciler deleteReconciler;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        instanceRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void should_StartStoppedInstance_when_ProviderIsCreated() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("start-created-tenant", 4, 2048, 4)
        );
        Instance instance = createStoppedInstance(tenant);
        commandService.startInstance(tenant.getId(), instance.getId());
        ClaimedTask startTask = claim(TaskType.START_INSTANCE);

        ProviderResource created = resource("container-start", ProviderResourceStatus.CREATED);
        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.of(created), Optional.of(
                        resource("container-start", ProviderResourceStatus.RUNNING)
                ));

        startHandler.handle(startTask);
        taskStateService.markSuccess(startTask.taskId(), startTask.claimToken());

        assertThat(instance(instance.getId()).getCurrentState()).isEqualTo(CurrentState.RUNNING);
        assertThat(task(startTask.taskId()).getStatus()).isEqualTo(TaskStatus.SUCCESS);
        verify(computeProvider).start("container-start");
    }

    @Test
    void should_BeIdempotent_when_StartTaskFindsRunningProvider() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("start-running-tenant", 4, 2048, 4)
        );
        Instance instance = createStoppedInstance(tenant);
        commandService.startInstance(tenant.getId(), instance.getId());
        ClaimedTask startTask = claim(TaskType.START_INSTANCE);

        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.of(resource("container-running", ProviderResourceStatus.RUNNING)));

        startHandler.handle(startTask);
        startHandler.handle(startTask);
        taskStateService.markSuccess(startTask.taskId(), startTask.claimToken());

        assertThat(instance(instance.getId()).getCurrentState()).isEqualTo(CurrentState.RUNNING);
        verify(computeProvider, org.mockito.Mockito.never()).start(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void should_RetryStart_when_ProviderIsTemporarilyMissing() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("start-retry-tenant", 4, 2048, 4)
        );
        Instance instance = createStoppedInstance(tenant);
        commandService.startInstance(tenant.getId(), instance.getId());
        ClaimedTask firstClaim = claim(TaskType.START_INSTANCE);

        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.empty());

        try {
            startHandler.handle(firstClaim);
        } catch (IllegalStateException exception) {
            taskExecutionService.handleFailure(firstClaim, exception);
        }

        assertThat(instance(instance.getId()).getCurrentState()).isEqualTo(CurrentState.STARTING);
        makeAvailable(firstClaim.taskId());

        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.of(resource("container-retry", ProviderResourceStatus.CREATED)),
                        Optional.of(resource("container-retry", ProviderResourceStatus.RUNNING)));

        ClaimedTask retryClaim = claim(TaskType.START_INSTANCE);
        startHandler.handle(retryClaim);
        taskStateService.markSuccess(retryClaim.taskId(), retryClaim.claimToken());

        assertThat(instance(instance.getId()).getCurrentState()).isEqualTo(CurrentState.RUNNING);
        assertThat(task(retryClaim.taskId()).getStatus()).isEqualTo(TaskStatus.SUCCESS);
    }

    @Test
    void should_StopRunningInstance_when_ProviderStopsSuccessfully() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("stop-running-tenant", 4, 2048, 4)
        );
        Instance instance = createRunningInstance(tenant);
        commandService.stopInstance(tenant.getId(), instance.getId());
        ClaimedTask stopTask = claim(TaskType.STOP_INSTANCE);

        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.of(resource("container-stop", ProviderResourceStatus.RUNNING)),
                        Optional.of(resource("container-stop", ProviderResourceStatus.STOPPED)));

        stopHandler.handle(stopTask);
        taskStateService.markSuccess(stopTask.taskId(), stopTask.claimToken());

        assertThat(instance(instance.getId()).getCurrentState()).isEqualTo(CurrentState.STOPPED);
        assertThat(instance(instance.getId()).getDesiredState()).isEqualTo(DesiredState.STOPPED);
        verify(computeProvider).stop("container-stop");
    }

    @Test
    void should_StopWithoutProviderResource_when_ProviderResourceIsMissing() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("stop-missing-tenant", 4, 2048, 4)
        );
        Instance instance = createRunningInstance(tenant);
        commandService.stopInstance(tenant.getId(), instance.getId());
        ClaimedTask stopTask = claim(TaskType.STOP_INSTANCE);

        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.empty());

        stopHandler.handle(stopTask);
        taskStateService.markSuccess(stopTask.taskId(), stopTask.claimToken());

        Instance stopped = instance(instance.getId());
        assertThat(stopped.getCurrentState()).isEqualTo(CurrentState.STOPPED);
        assertThat(stopped.getContainerId()).isNull();
    }

    @Test
    void should_RetryStop_when_ProviderStopTemporarilyFails() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("stop-retry-tenant", 4, 2048, 4)
        );
        Instance instance = createRunningInstance(tenant);
        commandService.stopInstance(tenant.getId(), instance.getId());
        ClaimedTask firstClaim = claim(TaskType.STOP_INSTANCE);

        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.of(resource("container-stop-retry", ProviderResourceStatus.RUNNING)));
        doThrow(new IllegalStateException("provider stop unavailable"))
                .when(computeProvider).stop("container-stop-retry");

        try {
            stopHandler.handle(firstClaim);
        } catch (IllegalStateException exception) {
            taskExecutionService.handleFailure(firstClaim, exception);
        }

        assertThat(instance(instance.getId()).getCurrentState()).isEqualTo(CurrentState.STOPPING);
        makeAvailable(firstClaim.taskId());
        reset(computeProvider);
        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.of(resource("container-stop-retry", ProviderResourceStatus.RUNNING)),
                        Optional.of(resource("container-stop-retry", ProviderResourceStatus.STOPPED)));

        ClaimedTask retryClaim = claim(TaskType.STOP_INSTANCE);
        stopHandler.handle(retryClaim);
        taskStateService.markSuccess(retryClaim.taskId(), retryClaim.claimToken());

        assertThat(instance(instance.getId()).getCurrentState()).isEqualTo(CurrentState.STOPPED);
        assertThat(task(retryClaim.taskId()).getStatus()).isEqualTo(TaskStatus.SUCCESS);
    }

    @Test
    void should_DeleteStoppedInstance_when_ProviderResourceIsRemoved() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("delete-resource-tenant", 4, 2048, 4)
        );
        Instance instance = createStoppedInstance(tenant);
        commandService.deleteInstance(tenant.getId(), instance.getId());
        ClaimedTask deleteTask = claim(TaskType.DELETE_INSTANCE);

        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.of(resource("container-delete", ProviderResourceStatus.STOPPED)),
                        Optional.empty());

        deleteHandler.handle(deleteTask);
        taskStateService.markSuccess(deleteTask.taskId(), deleteTask.claimToken());

        Instance deleted = instance(instance.getId());
        assertThat(deleted.getCurrentState()).isEqualTo(CurrentState.DELETED);
        assertThat(deleted.getDesiredState()).isEqualTo(DesiredState.DELETED);
        assertThat(deleted.getContainerId()).isNull();
        assertQuotaReleased(tenant);
        verify(computeProvider).delete("container-delete");
    }

    @Test
    void should_RetryDelete_when_ProviderDeleteTemporarilyFails() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("delete-retry-tenant", 4, 2048, 4)
        );
        Instance instance = createStoppedInstance(tenant);
        commandService.deleteInstance(tenant.getId(), instance.getId());
        ClaimedTask firstClaim = claim(TaskType.DELETE_INSTANCE);

        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.of(resource("container-delete-retry", ProviderResourceStatus.STOPPED)));
        doThrow(new IllegalStateException("provider delete unavailable"))
                .when(computeProvider).delete("container-delete-retry");

        try {
            deleteHandler.handle(firstClaim);
        } catch (IllegalStateException exception) {
            taskExecutionService.handleFailure(firstClaim, exception);
        }

        assertThat(instance(instance.getId()).getCurrentState()).isEqualTo(CurrentState.DELETING);
        makeAvailable(firstClaim.taskId());
        reset(computeProvider);
        when(computeProvider.findByInstanceId(instance.getId()))
                .thenReturn(Optional.of(resource("container-delete-retry", ProviderResourceStatus.STOPPED)),
                        Optional.empty());

        ClaimedTask retryClaim = claim(TaskType.DELETE_INSTANCE);
        deleteHandler.handle(retryClaim);
        taskStateService.markSuccess(retryClaim.taskId(), retryClaim.claimToken());

        assertThat(instance(instance.getId()).getCurrentState()).isEqualTo(CurrentState.DELETED);
        assertThat(task(retryClaim.taskId()).getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertQuotaReleased(tenant);
    }

    @Test
    void should_NotCreateDuplicateCommandTasks_when_StartStopDeleteIsRepeated() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("idempotent-command-tenant", 8, 4096, 8)
        );
        Instance instance = createStoppedInstance(tenant);

        commandService.startInstance(tenant.getId(), instance.getId());
        commandService.startInstance(tenant.getId(), instance.getId());
        assertThat(taskRepository.findAll().stream()
                .filter(task -> task.getType() == TaskType.START_INSTANCE)
                .count()).isEqualTo(1);

        ClaimedTask startTask = claim(TaskType.START_INSTANCE);
        taskStateService.retry(startTask, Instant.now().minusSeconds(1), "test retry");
        commandService.stopInstance(tenant.getId(), instance.getId());
        commandService.stopInstance(tenant.getId(), instance.getId());

        assertThat(taskRepository.findAll().stream()
                .filter(task -> task.getType() == TaskType.STOP_INSTANCE)
                .count()).isEqualTo(1);

        Instance deleteTarget = createStoppedInstance(tenant);
        commandService.deleteInstance(tenant.getId(), deleteTarget.getId());
        commandService.deleteInstance(tenant.getId(), deleteTarget.getId());

        assertThat(taskRepository.findAll().stream()
                .filter(task -> task.getType() == TaskType.DELETE_INSTANCE)
                .count()).isEqualTo(1);
    }

    private Instance createStoppedInstance(Tenant tenant) {
        InstanceResponse response = commandService.createInstance(
                tenant.getId(),
                new CreateInstanceRequest(
                        "instance-" + UUID.randomUUID(),
                        "nginx:latest",
                        1,
                        512
                )
        );
        ClaimedTask createTask = claim(TaskType.CREATE_INSTANCE);
        commandService.stopInstance(tenant.getId(), response.id());
        lifecycleService.completeStop(createTask, true);
        taskStateService.markSuccess(createTask.taskId(), createTask.claimToken());
        return instance(response.id());
    }

    private Instance createRunningInstance(Tenant tenant) {
        InstanceResponse response = commandService.createInstance(
                tenant.getId(),
                new CreateInstanceRequest(
                        "instance-" + UUID.randomUUID(),
                        "nginx:latest",
                        1,
                        512
                )
        );
        ClaimedTask createTask = claim(TaskType.CREATE_INSTANCE);
        when(computeProvider.findByInstanceId(response.id()))
                .thenReturn(Optional.of(resource("container-running", ProviderResourceStatus.CREATED)));
        createHandler.handle(createTask);
        taskStateService.markSuccess(createTask.taskId(), createTask.claimToken());
        reset(computeProvider);
        return instance(response.id());
    }

    private ClaimedTask claim(TaskType type) {
        return taskClaimService.claimTasks(10, "test-worker").stream()
                .filter(task -> task.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No queued task of type " + type));
    }

    private void makeAvailable(UUID taskId) {
        jdbcTemplate.update(
                "UPDATE tasks SET available_at = now() - interval '1 second' WHERE id = ?",
                taskId
        );
    }

    private Instance instance(UUID id) {
        return instanceRepository.findById(id).orElseThrow();
    }

    private Task task(UUID id) {
        return taskRepository.findById(id).orElseThrow();
    }

    private ProviderResource resource(String id, ProviderResourceStatus status) {
        return new ProviderResource(id, "polaris-" + id, status);
    }

    private void assertQuotaReleased(Tenant tenant) {
        Tenant updatedTenant = tenantRepository.findById(tenant.getId()).orElseThrow();

        assertThat(updatedTenant.getAllocatedCpu()).isZero();
        assertThat(updatedTenant.getAllocatedRamMb()).isZero();
        assertThat(updatedTenant.getAllocatedInstanceCount()).isZero();
    }
}
