package com.cloud.polaris.task.handler;

import com.cloud.polaris.instance.service.InstanceService;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.CreateContainerRequest;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CreateInstanceHandler implements TaskHandler {

    private final ComputeProvider computeProvider;
    private final InstanceService instanceService;

    @Override
    public TaskType supportedType() {
        return TaskType.CREATE_INSTANCE;
    }

    @Override
    public void handle(ClaimedTask task) {
        instanceService.markProvisioning(task.instanceId());

        ProviderResource resource = computeProvider.findByInstanceId(task.instanceId())
                .orElseGet(() ->
                        computeProvider.createContainer(
                                toCreateRequest(task)));
    }

    private CreateContainerRequest toCreateRequest(ClaimedTask task) {
        JsonNode payload = task.payload();

        String name = payload.get("name").asText();
        String imageName = payload.get("imageName").asText();
        int cpuAllocated = payload.get("cpuAllocated").asInt();
        int ramMb = payload.get("ramMb").asInt();

        Map<String, String> labels = Map.of(
                "polaris.managed", "true",
                "polaris.instance_id", task.instanceId().toString(),
                "polaris.tenant_id", task.tenantId().toString()
        );

        return new CreateContainerRequest(
                task.instanceId(),
                task.tenantId(),
                "polaris-" + task.instanceId(),
                imageName,
                cpuAllocated,
                ramMb,
                labels
        );



    }

}
