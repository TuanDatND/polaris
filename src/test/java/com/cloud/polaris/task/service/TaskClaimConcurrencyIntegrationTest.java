package com.cloud.polaris.task.service;

import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.domain.TaskStatus;
import com.cloud.polaris.task.repository.TaskRepository;
import com.cloud.polaris.tenant.domain.Tenant;
import com.cloud.polaris.tenant.repository.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class TaskClaimConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TaskService taskService;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    InstanceRepository instanceRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    ObjectMapper objectMapper;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        instanceRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void should_ClaimEachQueuedTaskAtMostOnce_when_TwoWorkersClaimConcurrently() throws Exception {
        // Given: exactly 10 visible queued tasks.
        Tenant tenant = tenantRepository.saveAndFlush(Tenant.create("concurrency-tenant", 20, 20_000, 10));
        List<Instance> instances = instanceRepository.saveAllAndFlush(
                java.util.stream.IntStream.range(0, 10)
                        .mapToObj(i -> Instance.createPending(tenant, "instance-" + i, "nginx:latest", 1, 512))
                        .toList());

        taskRepository.saveAllAndFlush(instances.stream()
                .map(instance -> Task.createInstanceTask(tenant, instance, objectMapper.createObjectNode()))
                .toList());

        CountDownLatch startGate = new CountDownLatch(1);
        Set<UUID> workerOneClaims = ConcurrentHashMap.newKeySet();
        Set<UUID> workerTwoClaims = ConcurrentHashMap.newKeySet();

        CompletableFuture<Void> workerOne = claimInParallel(startGate, "worker-1", workerOneClaims);
        CompletableFuture<Void> workerTwo = claimInParallel(startGate, "worker-2", workerTwoClaims);

        // When: both workers are released at the same time.
        startGate.countDown();
        CompletableFuture.allOf(workerOne, workerTwo).get(10, TimeUnit.SECONDS);

        // Then: all 10 tasks were claimed, and no task was claimed twice.
        Set<UUID> allClaims = ConcurrentHashMap.newKeySet();
        allClaims.addAll(workerOneClaims);
        allClaims.addAll(workerTwoClaims);


        assertThat(allClaims).hasSize(10);
        assertThat(workerOneClaims).noneMatch(workerTwoClaims::contains);
        assertThat(workerOneClaims.size() + workerTwoClaims.size()).isEqualTo(10);
        assertThat(taskRepository.findAll())
                .allMatch(task -> task.getStatus() == TaskStatus.RUNNING);
    }

    private CompletableFuture<Void> claimInParallel(
            CountDownLatch startGate,
            String workerId,
            Set<UUID> claims) {
        return CompletableFuture.runAsync(() -> {
            try {
                assertThat(startGate.await(5, TimeUnit.SECONDS)).isTrue();
                claims.addAll(taskService.claimTasks(10, workerId).stream().map(ClaimedTask::taskId).toList());

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Worker was interrupted", exception);
            }
        });
    }
}
