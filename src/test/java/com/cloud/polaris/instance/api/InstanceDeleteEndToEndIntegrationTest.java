package com.cloud.polaris.instance.api;

import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.reconcile.controller.ReconcileController;
import com.cloud.polaris.reconcile.queue.ClaimedReconcileRequest;
import com.cloud.polaris.reconcile.queue.ReconcileQueueService;
import com.cloud.polaris.reconcile.queue.ReconcileRequestRepository;
import com.cloud.polaris.task.repository.TaskRepository;
import com.cloud.polaris.task.worker.TaskRecovery;
import com.cloud.polaris.task.worker.TaskWorker;
import com.cloud.polaris.tenant.repository.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(properties = {
        "polaris.reconcile.worker-enabled=false",
        "polaris.legacy-reconciliation.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
class InstanceDeleteEndToEndIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    ReconcileQueueService reconcileQueueService;
    @Autowired
    ReconcileController reconcileController;
    @Autowired
    TenantRepository tenantRepository;
    @Autowired
    InstanceRepository instanceRepository;
    @Autowired
    TaskRepository taskRepository;
    @Autowired
    ReconcileRequestRepository reconcileRequestRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    com.cloud.polaris.provider.ComputeProvider computeProvider;
    @MockitoBean
    TaskWorker taskWorker;
    @MockitoBean
    TaskRecovery taskRecovery;

    @AfterEach
    void cleanDatabase() {
        reconcileRequestRepository.deleteAll();
        taskRepository.deleteAll();
        instanceRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void should_StopDeleteAndReleaseQuotaExactlyOnce_when_DeleteControllerFlowRunsEndToEnd()
            throws Exception {
        UUID tenantId = createTenant();
        UUID instanceId = createInstance(tenantId);

        jdbcTemplate.update(
                "UPDATE instances SET current_state = 'RUNNING', container_id = ? WHERE id = ?",
                "container-e2e",
                instanceId
        );

        when(computeProvider.findByInstanceId(instanceId))
                .thenReturn(
                        Optional.of(resource("container-e2e", com.cloud.polaris.provider.ProviderResourceStatus.RUNNING)),
                        Optional.of(resource("container-e2e", com.cloud.polaris.provider.ProviderResourceStatus.STOPPED)),
                        Optional.empty(),
                        Optional.empty()
                );

        mockMvc.perform(delete("/api/v1/instances/{instanceId}", instanceId)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.desiredState").value("DELETED"))
                .andExpect(jsonPath("$.currentState").value("RUNNING"));

        assertThat(instance(instanceId).getCurrentState()).isEqualTo(CurrentState.RUNNING);

        runOneReconcileCycle(instanceId);
        assertThat(instance(instanceId).getCurrentState()).isEqualTo(CurrentState.STOPPING);

        makeRequestAvailable(instanceId);
        runOneReconcileCycle(instanceId);
        assertThat(instance(instanceId).getCurrentState()).isEqualTo(CurrentState.DELETING);

        makeRequestAvailable(instanceId);
        runOneReconcileCycle(instanceId);
        var deleted = instance(instanceId);
        assertThat(deleted.getDesiredState()).isEqualTo(com.cloud.polaris.instance.domain.DesiredState.DELETED);
        assertThat(deleted.getCurrentState()).isEqualTo(CurrentState.DELETED);
        assertThat(deleted.getContainerId()).isNull();
        assertThat(deleted.isQuotaReleased()).isTrue();
        assertQuota(0, 0, 0, tenantId);

        makeRequestAvailable(instanceId);
        runOneReconcileCycle(instanceId);
        assertQuota(0, 0, 0, tenantId);

        verify(computeProvider, times(1)).stop("container-e2e");
        verify(computeProvider, times(1)).delete("container-e2e");
        verify(computeProvider, times(4)).findByInstanceId(instanceId);
    }

    private UUID createTenant() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "delete-e2e-tenant",
                                  "quotaCpu": 2,
                                  "quotaRamMb": 1024,
                                  "quotaInstanceCount": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }

    private UUID createInstance(UUID tenantId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/instances")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "delete-e2e-instance",
                                  "imageName": "nginx:latest",
                                  "cpu": 1,
                                  "ramMb": 512
                                }
                                """))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void runOneReconcileCycle(UUID instanceId) {
        ClaimedReconcileRequest claimed = reconcileQueueService
                .claimReady(1, "e2e-test-worker", Duration.ofMinutes(1))
                .stream()
                .filter(request -> request.instanceId().equals(instanceId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ready reconcile request"));

        reconcileController.reconcile(claimed);
    }

    private void makeRequestAvailable(UUID instanceId) {
        jdbcTemplate.update(
                "UPDATE reconcile_requests SET available_at = now() - interval '1 second' WHERE instance_id = ?",
                instanceId
        );
    }

    private void assertQuota(int cpu, int ramMb, int count, UUID tenantId) {
        var tenant = tenantRepository.findById(tenantId).orElseThrow();
        assertThat(tenant.getAllocatedCpu()).isEqualTo(cpu);
        assertThat(tenant.getAllocatedRamMb()).isEqualTo(ramMb);
        assertThat(tenant.getAllocatedInstanceCount()).isEqualTo(count);
    }

    private com.cloud.polaris.instance.domain.Instance instance(UUID instanceId) {
        return instanceRepository.findById(instanceId).orElseThrow();
    }

    private com.cloud.polaris.provider.ProviderResource resource(
            String id,
            com.cloud.polaris.provider.ProviderResourceStatus status
    ) {
        return new com.cloud.polaris.provider.ProviderResource(
                id,
                "polaris-" + id,
                status
        );
    }
}
