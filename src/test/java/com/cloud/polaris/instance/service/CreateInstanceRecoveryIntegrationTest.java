package com.cloud.polaris.instance.service;

import com.cloud.polaris.common.exception.StaleTaskOwnerException;
import com.cloud.polaris.instance.api.CreateInstanceRequest;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.provider.ComputeProvider;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
class CreateInstanceRecoveryIntegrationTest {

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
    TaskClaimService taskClaimService;
    @Autowired
    TaskExecutionService taskExecutionService;
    @Autowired
    TaskStateService taskStateService;
    @Autowired
    InstanceCompensationService instanceCompensationService;
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
    void should_RequeueStaleCreateTask_when_WorkerDiesAfterClaim()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("recovery-tenant", 2, 1024, 2)
        );
        UUID instanceId = createInstance(tenant).getId();

        ClaimedTask firstClaim = taskClaimService
                .claimTasks(1, "worker-crashed")
                .getFirst();

        jdbcTemplate.update(
                "UPDATE tasks SET locked_at = now() - interval '60 seconds' WHERE id = ?",
                firstClaim.taskId()
        );

        int recovered = taskClaimService.recoverExpiredTasks(
                Duration.ofSeconds(1),
                10
        );

        Task recoveredTask = taskRepository.findById(firstClaim.taskId())
                .orElseThrow();

        assertThat(recovered).isEqualTo(1);
        assertThat(recoveredTask.getStatus()).isEqualTo(TaskStatus.QUEUED);
        assertThat(recoveredTask.getClaimToken()).isNull();
        assertThat(recoveredTask.getLockedBy()).isNull();

        ClaimedTask secondClaim = taskClaimService
                .claimTasks(1, "worker-recovered")
                .getFirst();

        assertThat(secondClaim.taskId()).isEqualTo(firstClaim.taskId());
        assertThat(secondClaim.instanceId()).isEqualTo(instanceId);
        assertThat(secondClaim.attempts()).isEqualTo(2);
    }

    @Test
    void should_MarkCreateAsFailedAndReleaseQuota_when_RetriesExhausted()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("failure-tenant", 2, 1024, 2)
        );
        UUID instanceId = createInstance(tenant).getId();

        for (int retry = 1; retry <= 4; retry++) {
            ClaimedTask claimedTask = taskClaimService
                    .claimTasks(1, "worker-" + retry)
                    .getFirst();

            taskStateService.retry(
                    claimedTask,
                    Instant.now().minusSeconds(1),
                    "provider unavailable"
            );
        }

        ClaimedTask finalClaim = taskClaimService
                .claimTasks(1, "worker-final")
                .getFirst();

        when(computeProvider.findByInstanceId(instanceId))
                .thenReturn(Optional.empty());

        taskExecutionService.handleFailure(
                finalClaim,
                new IllegalStateException("provider unavailable")
        );

        Task failedTask = taskRepository.findById(finalClaim.taskId())
                .orElseThrow();
        Instance failedInstance = instanceRepository.findById(instanceId)
                .orElseThrow();
        Tenant updatedTenant = tenantRepository.findById(tenant.getId())
                .orElseThrow();

        assertThat(failedTask.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(failedInstance.getCurrentState()).isEqualTo(CurrentState.FAILED);
        assertThat(failedInstance.isQuotaReleased()).isTrue();
        assertThat(failedInstance.getFailureReason()).isEqualTo("provider unavailable");
        assertThat(updatedTenant.getAllocatedCpu()).isZero();
        assertThat(updatedTenant.getAllocatedRamMb()).isZero();
        assertThat(updatedTenant.getAllocatedInstanceCount()).isZero();

        instanceCompensationService.releaseQuotaIfCleanupCompleted(instanceId);

        Tenant afterSecondReleaseAttempt = tenantRepository
                .findById(tenant.getId())
                .orElseThrow();
        assertThat(afterSecondReleaseAttempt.getAllocatedCpu()).isZero();
        assertThat(afterSecondReleaseAttempt.getAllocatedRamMb()).isZero();
        assertThat(afterSecondReleaseAttempt.getAllocatedInstanceCount()).isZero();
    }

    @Test
    void should_RejectStaleClaimToken_withoutMutatingCreateState()
            throws Exception {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create("stale-token-tenant", 2, 1024, 2)
        );
        UUID instanceId = createInstance(tenant).getId();
        ClaimedTask claimedTask = taskClaimService
                .claimTasks(1, "worker-one")
                .getFirst();

        ClaimedTask staleClaim = new ClaimedTask(
                claimedTask.taskId(),
                claimedTask.type(),
                claimedTask.instanceId(),
                claimedTask.tenantId(),
                claimedTask.payload(),
                claimedTask.attempts(),
                claimedTask.maxAttempts(),
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> instanceCompensationService.finalizeFailure(
                staleClaim,
                "stale worker failure",
                true
        )).isInstanceOf(StaleTaskOwnerException.class);

        Task task = taskRepository.findById(claimedTask.taskId()).orElseThrow();
        Instance instance = instanceRepository.findById(instanceId).orElseThrow();
        Tenant unchangedTenant = tenantRepository.findById(tenant.getId()).orElseThrow();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(instance.getCurrentState()).isEqualTo(CurrentState.PENDING);
        assertThat(instance.isQuotaReleased()).isFalse();
        assertThat(unchangedTenant.getAllocatedCpu()).isEqualTo(1);
        assertThat(unchangedTenant.getAllocatedRamMb()).isEqualTo(512);
        assertThat(unchangedTenant.getAllocatedInstanceCount()).isEqualTo(1);
    }

    private Instance createInstance(Tenant tenant) {
        UUID instanceId = instanceCommandService.createInstance(
                tenant.getId(),
                new CreateInstanceRequest(
                        "recovery-instance-" + UUID.randomUUID(),
                        "nginx:latest",
                        1,
                        512
                )
        ).id();

        return instanceRepository.findById(instanceId).orElseThrow();
    }
}
