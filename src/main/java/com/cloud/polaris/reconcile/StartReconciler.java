package com.cloud.polaris.reconcile;

import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.instance.service.InstanceLifecycleService;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.provider.ProviderResourceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartReconciler {

    private final InstanceRepository instanceRepository;
    private final ComputeProvider computeProvider;
    private final InstanceLifecycleService instanceLifecycleService;

    @Scheduled(fixedDelay = 30_000)
    public void reconcile() {
        List<UUID> instanceIds = instanceRepository.findInstanceIdsForStartReconciliation(
                CurrentState.STARTING,
                DesiredState.RUNNING,
                PageRequest.of(0, 50)
        );

        for (UUID id : instanceIds) {
            try {
                reconcileOne(id);
            } catch (Exception exception) {
                log.error("Exception occurred while trying to reconcile instanceId {}", id, exception);
            }
        }
    }

    private void reconcileOne(UUID instanceId) {
        Optional<ProviderResource> resource = computeProvider.findByInstanceId(instanceId);

        if (resource.isEmpty()) {
            log.warn("Provider resource missing for starting instance {}",
                    instanceId);
            return;
        }

        if (resource.get().status() == ProviderResourceStatus.RUNNING) {
            instanceLifecycleService.completeStartFromReconciliation(instanceId, resource.get().providerResourceId());
            return;
        }

        if(resource.get().status() == ProviderResourceStatus.STOPPED || resource.get().status() == ProviderResourceStatus.CREATED){
            log.info(
                    "Starting provider resource again for instance {}",
                    instanceId
            );
            computeProvider.start(
                    resource.get().providerResourceId()
            );

            return;
        }

        if (resource.get().status() == ProviderResourceStatus.UNKNOWN) {
            log.warn("Provider resource status unknown for instance {}",
                    instanceId);
            return;
        }

        log.info("Instance {} is STARTING but provider status is {}",
                instanceId,
                resource.get().status());

    }
}
