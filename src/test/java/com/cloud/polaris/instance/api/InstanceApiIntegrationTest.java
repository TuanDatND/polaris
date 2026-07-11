package com.cloud.polaris.instance.api;



import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.tenant.domain.Tenant;
import com.cloud.polaris.tenant.repository.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Disabled("Temporarily disabled because local Docker/Testcontainers setup is unstable on Windows")
class InstanceApiIntegrationTest {

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
    TenantRepository tenantRepository;
    @Autowired
    InstanceRepository instanceRepository;
    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        instanceRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void should_CreateInstance_when_RequestValid() throws Exception {
        Tenant tenant = tenantRepository.save(Tenant.create("tenant-a", 2, 1024, 2));

        mockMvc.perform(post("/api/v1/instances")
                        .header("X-Tenant-Id", tenant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "nginx-1",
                                  "imageName": "nginx:latest",
                                  "cpu": 1,
                                  "ramMb": 512
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("nginx-1"))
                .andExpect(jsonPath("$.imageName").value("nginx:latest"))
                .andExpect(jsonPath("$.desiredState").value("RUNNING"))
                .andExpect(jsonPath("$.currentState").value("PENDING"));

        Tenant updatedTenant = tenantRepository.findById(tenant.getId()).orElseThrow();

        assertThat(updatedTenant.getAllocatedCpu()).isEqualTo(1);
        assertThat(updatedTenant.getAllocatedRamMb()).isEqualTo(512);
        assertThat(updatedTenant.getAllocatedInstanceCount()).isEqualTo(1);
    }

    @Test
    void should_RejectCreateInstance_when_QuotaExceeded() throws Exception {
        Tenant tenant = tenantRepository.save(Tenant.create("tenant-a", 1, 512, 1));

        mockMvc.perform(post("/api/v1/instances")
                        .header("X-Tenant-Id", tenant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "too-big",
                                  "imageName": "nginx:latest",
                                  "cpu": 2,
                                  "ramMb": 1024
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));

        Tenant updatedTenant = tenantRepository.findById(tenant.getId()).orElseThrow();

        assertThat(updatedTenant.getAllocatedCpu()).isZero();
        assertThat(updatedTenant.getAllocatedRamMb()).isZero();
        assertThat(updatedTenant.getAllocatedInstanceCount()).isZero();
    }

    @Test
    void should_RejectCreateInstance_when_NameDuplicatedInSameTenant() throws Exception {
        Tenant tenant = tenantRepository.save(Tenant.create("tenant-a", 4, 2048, 5));

        String body = """
                {
                  "name": "nginx-1",
                  "imageName": "nginx:latest",
                  "cpu": 1,
                  "ramMb": 512
                }
                """;

        mockMvc.perform(post("/api/v1/instances")
                        .header("X-Tenant-Id", tenant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/instances")
                        .header("X-Tenant-Id", tenant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void should_ReturnValidationError_when_CpuInvalid() throws Exception {
        Tenant tenant = tenantRepository.save(Tenant.create("tenant-a", 2, 1024, 2));

        mockMvc.perform(post("/api/v1/instances")
                        .header("X-Tenant-Id", tenant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "bad-instance",
                                  "imageName": "nginx:latest",
                                  "cpu": 0,
                                  "ramMb": 512
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void should_GetInstance_when_InstanceBelongsToTenant() throws Exception {
        Tenant tenant = tenantRepository.save(Tenant.create("tenant-a", 2, 1024, 2));

        String response = mockMvc.perform(post("/api/v1/instances")
                        .header("X-Tenant-Id", tenant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "nginx-1",
                                  "imageName": "nginx:latest",
                                  "cpu": 1,
                                  "ramMb": 512
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        UUID instanceId = UUID.fromString(json.get("id").asText());

        mockMvc.perform(get("/api/v1/instances/{id}", instanceId)
                        .header("X-Tenant-Id", tenant.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(instanceId.toString()))
                .andExpect(jsonPath("$.desiredState").value("RUNNING"))
                .andExpect(jsonPath("$.currentState").value("PENDING"));
    }
}
