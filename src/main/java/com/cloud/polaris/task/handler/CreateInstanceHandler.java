package com.cloud.polaris.task.handler;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.instance.service.InstanceLifecycleService;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.CreateContainerRequest;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.provider.ProviderResourceStatus;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.TaskType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateInstanceHandler implements TaskHandler {

    private final ComputeProvider computeProvider;
    private final InstanceLifecycleService instanceLifecycleService;
    private final InstanceRepository instanceRepository;

    @Override
    public TaskType supportedType() {
        return TaskType.CREATE_INSTANCE;
    }

    @Override
    public void handle(ClaimedTask task) {
        ProviderResource resource = computeProvider.findByInstanceId(task.instanceId())
                .orElse(null);


        if (resource != null && resource.status() == ProviderResourceStatus.RUNNING) {
            //check desired state
            boolean markedRunning =
                    instanceLifecycleService.markRunning(
                            task,
                            resource.providerResourceId()
                    );

            if (!markedRunning) {
                computeProvider.stop(resource.providerResourceId());
                instanceLifecycleService.completeStop(task, false);
            }
            return;
        }

        instanceLifecycleService.ensureProvisioning(task.instanceId());

        boolean createdInThisAttempt = false;

        if (resource == null) {
            resource = computeProvider.createContainer(toCreateRequest(task));
            createdInThisAttempt = true;
        }

        Instance instance = instanceRepository.findById(task.instanceId()).orElseThrow(() -> new ResourceNotFoundException("Instance not found "));
        //for stop task when create task doing
        if (instance.getDesiredState() != DesiredState.RUNNING ){
            if (resource == null
                    || resource.status() == ProviderResourceStatus.CREATED
                    || resource.status() == ProviderResourceStatus.STOPPED) {

                if (createdInThisAttempt && resource != null) {
                    computeProvider.delete(resource.providerResourceId());
                }
                instanceLifecycleService.completeStop(task, createdInThisAttempt);
                return;
            }

            if (resource.status() == ProviderResourceStatus.RUNNING) {
                computeProvider.stop(resource.providerResourceId());
                    instanceLifecycleService.completeStop(task, false);
                return;
            }
        }

        boolean cleanupAttempted = false;

        try {
            if (resource.status() != ProviderResourceStatus.RUNNING) {
                computeProvider.start(resource.providerResourceId());
            }

            boolean markedRunning =
                    instanceLifecycleService.markRunning(
                            task,
                            resource.providerResourceId()
                    );

            if (!markedRunning) {
                cleanupAttempted = true;

                boolean cleaned;

                if (createdInThisAttempt) {
                    cleaned = cleanupResource(resource);
                } else {
                    computeProvider.stop(resource.providerResourceId());
                    cleaned = true;
                }

                if (!cleaned) {
                    throw new IllegalStateException(
                            "Failed to cleanup resource after desired state changed"
                    );
                }

                instanceLifecycleService.completeStop(task, createdInThisAttempt);
            }
        } catch (Exception e) {
            if (createdInThisAttempt && !cleanupAttempted) {
                cleanupResource(resource);
            }
            throw e;
        }
    }

    private boolean cleanupResource(ProviderResource resource) {
        try {
            computeProvider.delete(resource.providerResourceId());
            return true;
        } catch (Exception exception) {
            log.error(
                    "Failed to cleanup provider resource {}",
                    resource.providerResourceId(),
                    exception
            );
            return false;
        }
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
