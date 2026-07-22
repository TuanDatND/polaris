package com.cloud.polaris.task.handler;

import com.cloud.polaris.instance.api.CreateInstanceRequest;
import com.cloud.polaris.instance.api.InstanceResponse;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.instance.service.InstanceCommandService;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.provider.ProviderResourceStatus;
import com.cloud.polaris.reconcile.InstanceReconciler;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.domain.TaskStatus;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
class CreateInstanceSelfHealingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    InstanceCommandService instanceCommandService;
    @Autowired
    CreateInstanceHandler createInstanceHandler;
    @Autowired
    TaskClaimService taskClaimService;
    @Autowired
    TaskStateService taskStateService;
    @Autowired
    TaskExecutionService taskExecutionService;
    @Autowired
    InstanceReconciler instanceReconciler;
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

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        instanceRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void should_RecoverCreateTaskAfterTransientProviderFailure() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("self-healing-tenant", 2, 1024, 2)
        );
        InstanceResponse response = instanceCommandService.createInstance(
                tenant.getId(),
                new CreateInstanceRequest(
                        "self-healing-instance",
                        "nginx:latest",
                        1,
                        512
                )
        );

        UUID instanceId = response.id();
        ClaimedTask firstClaim = taskClaimService
                .claimTasks(1, "worker-first-attempt")
                .getFirst();

        when(computeProvider.findByInstanceId(instanceId))
                .thenReturn(Optional.empty(), Optional.empty());
        when(computeProvider.createContainer(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("provider temporarily unavailable"));

        try {
            createInstanceHandler.handle(firstClaim);
        } catch (IllegalStateException exception) {
            taskExecutionService.handleFailure(firstClaim, exception);
        }

        Task retriedTask = taskRepository.findById(firstClaim.taskId())
                .orElseThrow();
        Instance provisioningInstance = instanceRepository.findById(instanceId)
                .orElseThrow();

        assertThat(retriedTask.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(provisioningInstance.getCurrentState())
                .isEqualTo(CurrentState.PROVISIONING);

        jdbcTemplate.update(
                "UPDATE tasks SET available_at = now() - interval '1 second' WHERE id = ?",
                firstClaim.taskId()
        );

        ProviderResource resource = new ProviderResource(
                "container-recovered",
                "polaris-recovered",
                ProviderResourceStatus.CREATED
        );
        doReturn(resource)
                .when(computeProvider)
                .createContainer(org.mockito.ArgumentMatchers.any());

        ClaimedTask retryClaim = taskClaimService
                .claimTasks(1, "worker-recovered")
                .getFirst();
        createInstanceHandler.handle(retryClaim);
        taskStateService.markSuccess(retryClaim.taskId(), retryClaim.claimToken());

        Task successfulTask = taskRepository.findById(retryClaim.taskId())
                .orElseThrow();
        Instance runningInstance = instanceRepository.findById(instanceId)
                .orElseThrow();

        assertThat(successfulTask.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(runningInstance.getCurrentState()).isEqualTo(CurrentState.RUNNING);
        assertThat(runningInstance.getContainerId()).isEqualTo("container-recovered");
        verify(computeProvider).start("container-recovered");
    }

    @Test
    void should_CleanupProviderResourceAndReleaseQuota_when_FirstCleanupFails() {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("cleanup-tenant", 2, 1024, 2)
        );
        UUID instanceId = instanceCommandService.createInstance(
                tenant.getId(),
                new CreateInstanceRequest(
                        "cleanup-instance",
                        "nginx:latest",
                        1,
                        512
                )
        ).id();

        ClaimedTask finalClaim = exhaustCreateTaskRetries();
        ProviderResource orphanedResource = new ProviderResource(
                "orphaned-container",
                "polaris-orphaned",
                ProviderResourceStatus.CREATED
        );

        when(computeProvider.findByInstanceId(instanceId))
                .thenReturn(Optional.of(orphanedResource),
                        Optional.of(orphanedResource),
                        Optional.empty());
        doThrow(new IllegalStateException("provider cleanup unavailable"))
                .doNothing()
                .when(computeProvider)
                .delete("orphaned-container");

        taskExecutionService.handleFailure(
                finalClaim,
                new IllegalStateException("create retries exhausted")
        );

        Tenant beforeReconcile = tenantRepository.findById(tenant.getId())
                .orElseThrow();
        assertThat(beforeReconcile.getAllocatedInstanceCount()).isEqualTo(1);

        instanceReconciler.reconcileFailedInstances();

        Tenant afterReconcile = tenantRepository.findById(tenant.getId())
                .orElseThrow();
        Instance cleanedInstance = instanceRepository.findById(instanceId)
                .orElseThrow();

        assertThat(cleanedInstance.getCurrentState()).isEqualTo(CurrentState.FAILED);
        assertThat(cleanedInstance.isQuotaReleased()).isTrue();
        assertThat(afterReconcile.getAllocatedCpu()).isZero();
        assertThat(afterReconcile.getAllocatedRamMb()).isZero();
        assertThat(afterReconcile.getAllocatedInstanceCount()).isZero();
        verify(computeProvider, org.mockito.Mockito.times(2))
                .delete("orphaned-container");
    }

    private ClaimedTask exhaustCreateTaskRetries() {
        Task task = taskRepository.findAll().stream().findFirst().orElseThrow();
        ClaimedTask finalClaim = null;

        for (int attempt = 1; attempt <= task.getMaxAttempts(); attempt++) {
            ClaimedTask claim = taskClaimService
                    .claimTasks(1, "worker-attempt-" + attempt)
                    .getFirst();
            finalClaim = claim;

            if (attempt < task.getMaxAttempts()) {
                taskStateService.retry(
                        claim,
                        Instant.now().minusSeconds(1),
                        "create failed"
                );
            }
        }

        return finalClaim;
    }
}
