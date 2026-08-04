package com.cloud.polaris.reconcile.queue;

import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.tenant.domain.Tenant;
import com.cloud.polaris.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReconcileQueueService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReconcileQueueServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    ReconcileQueueService reconcileQueueService;

    @Autowired
    ReconcileRequestRepository reconcileRequestRepository;

    @Autowired
    InstanceRepository instanceRepository;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        reconcileRequestRepository.deleteAll();
        instanceRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void should_KeepOneRequestAndHighestGeneration_whenWokenTwice() {
        Instance instance = createInstance("wake-twice");

        reconcileQueueService.wake(instance.getId(), 1L);
        reconcileQueueService.wake(instance.getId(), 2L);

        ReconcileRequest request = reconcileRequestRepository
                .findById(instance.getId())
                .orElseThrow();

        assertThat(reconcileRequestRepository.count()).isEqualTo(1);
        assertThat(request.getRequestedGeneration()).isEqualTo(2L);
        assertThat(request.getStatus()).isEqualTo(ReconcileRequestStatus.READY);
    }

    @Test
    void should_ClaimEachInstanceAtMostOnce_whenWorkersClaimConcurrently()
            throws Exception {
        List<Instance> instances = createInstances(10);
        instances.forEach(instance ->
                reconcileQueueService.wake(instance.getId(), 1L)
        );

        CountDownLatch startGate = new CountDownLatch(1);
        Set<UUID> workerOneClaims = ConcurrentHashMap.newKeySet();
        Set<UUID> workerTwoClaims = ConcurrentHashMap.newKeySet();

        CompletableFuture<Void> workerOne = claimInParallel(
                startGate,
                "worker-1",
                workerOneClaims
        );
        CompletableFuture<Void> workerTwo = claimInParallel(
                startGate,
                "worker-2",
                workerTwoClaims
        );

        startGate.countDown();
        CompletableFuture.allOf(workerOne, workerTwo)
                .get(10, TimeUnit.SECONDS);

        Set<UUID> allClaims = ConcurrentHashMap.newKeySet();
        allClaims.addAll(workerOneClaims);
        allClaims.addAll(workerTwoClaims);

        assertThat(allClaims).hasSize(10);
        assertThat(workerOneClaims)
                .noneMatch(workerTwoClaims::contains);
        assertThat(reconcileRequestRepository.findAll())
                .allMatch(request ->
                        request.getStatus() == ReconcileRequestStatus.RUNNING
                );
    }

    @Test
    void should_PreserveClaimOwnership_whenWakeOccursDuringRun() {
        Instance instance = createInstance("wake-during-run");
        reconcileQueueService.wake(instance.getId(), 1L);

        ClaimedReconcileRequest claimed = reconcileQueueService
                .claimReady(1, "worker-1", Duration.ofSeconds(30))
                .getFirst();

        ReconcileRequest beforeWake = reconcileRequestRepository
                .findById(instance.getId())
                .orElseThrow();
        Instant leaseBeforeWake = beforeWake.getLeaseExpiresAt();

        reconcileQueueService.wake(instance.getId(), 2L);

        ReconcileRequest afterWake = reconcileRequestRepository
                .findById(instance.getId())
                .orElseThrow();

        assertThat(afterWake.getRequestedGeneration()).isEqualTo(2L);
        assertThat(afterWake.getStatus())
                .isEqualTo(ReconcileRequestStatus.RUNNING);
        assertThat(afterWake.getClaimToken())
                .isEqualTo(claimed.claimToken());
        assertThat(afterWake.getClaimedBy()).isEqualTo("worker-1");
        assertThat(afterWake.getLeaseExpiresAt())
                .isEqualTo(leaseBeforeWake);
    }

    @Test
    void should_RecoverExpiredLease_andRequeueRequest() {
        Instance instance = createInstance("expired-lease");
        reconcileQueueService.wake(instance.getId(), 1L);
        reconcileQueueService.claimReady(
                1,
                "worker-1",
                Duration.ofSeconds(30)
        );

        jdbcTemplate.update(
                """
                UPDATE reconcile_requests
                SET lease_expires_at = now() - interval '1 second'
                WHERE instance_id = ?
                """,
                instance.getId()
        );

        int recovered = reconcileQueueService.recoverExpiredLeases(10);

        ReconcileRequest request = reconcileRequestRepository
                .findById(instance.getId())
                .orElseThrow();

        assertThat(recovered).isEqualTo(1);
        assertThat(request.getStatus()).isEqualTo(ReconcileRequestStatus.READY);
        assertThat(request.getClaimToken()).isNull();
        assertThat(request.getClaimedBy()).isNull();
        assertThat(request.getLeaseExpiresAt()).isNull();
        assertThat(request.getFailureCount()).isEqualTo(1);
    }

    @Test
    void should_RejectRequeueFromStaleWorker_afterRequestIsClaimedAgain() {
        Instance instance = createInstance("stale-worker");
        reconcileQueueService.wake(instance.getId(), 1L);

        ClaimedReconcileRequest staleClaim = reconcileQueueService
                .claimReady(1, "worker-1", Duration.ofSeconds(30))
                .getFirst();

        jdbcTemplate.update(
                """
                UPDATE reconcile_requests
                SET lease_expires_at = now() - interval '1 second'
                WHERE instance_id = ?
                """,
                instance.getId()
        );
        reconcileQueueService.recoverExpiredLeases(1);

        ClaimedReconcileRequest currentClaim = reconcileQueueService
                .claimReady(1, "worker-2", Duration.ofSeconds(30))
                .getFirst();
        ReconcileRequest beforeStaleRequeue = reconcileRequestRepository
                .findById(instance.getId())
                .orElseThrow();

        assertThatThrownBy(() -> reconcileQueueService.requeue(
                staleClaim,
                Instant.now().plusSeconds(10),
                "stale worker must not requeue"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stale reconcile request owner");

        ReconcileRequest afterStaleRequeue = reconcileRequestRepository
                .findById(instance.getId())
                .orElseThrow();

        assertThat(currentClaim.claimToken())
                .isNotEqualTo(staleClaim.claimToken());
        assertThat(afterStaleRequeue.getStatus())
                .isEqualTo(ReconcileRequestStatus.RUNNING);
        assertThat(afterStaleRequeue.getClaimToken())
                .isEqualTo(currentClaim.claimToken());
        assertThat(afterStaleRequeue.getClaimedBy()).isEqualTo("worker-2");
        assertThat(afterStaleRequeue.getLeaseExpiresAt())
                .isEqualTo(beforeStaleRequeue.getLeaseExpiresAt());
        assertThat(afterStaleRequeue.getFailureCount()).isEqualTo(1);
    }

    private CompletableFuture<Void> claimInParallel(
            CountDownLatch startGate,
            String workerId,
            Set<UUID> claims
    ) {
        return CompletableFuture.runAsync(() -> {
            try {
                assertThat(startGate.await(5, TimeUnit.SECONDS)).isTrue();

                claims.addAll(
                        reconcileQueueService.claimReady(
                                        10,
                                        workerId,
                                        Duration.ofSeconds(30)
                                )
                                .stream()
                                .map(ClaimedReconcileRequest::instanceId)
                                .toList()
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Worker was interrupted",
                        exception
                );
            }
        });
    }

    private Instance createInstance(String name) {
        return createInstances(List.of(name)).getFirst();
    }

    private List<Instance> createInstances(int count) {
        return createInstances(
                java.util.stream.IntStream.range(0, count)
                        .mapToObj(index -> "queue-instance-" + index)
                        .toList()
        );
    }

    private List<Instance> createInstances(List<String> names) {
        Tenant tenant = tenantRepository.saveAndFlush(
                Tenant.create(
                        "reconcile-queue-" + UUID.randomUUID(),
                        100,
                        100_000,
                        100
                )
        );

        return instanceRepository.saveAllAndFlush(
                names.stream()
                        .map(name -> Instance.createPending(
                                tenant,
                                name,
                                "nginx:latest",
                                1,
                                512
                        ))
                        .toList()
        );
    }
}
