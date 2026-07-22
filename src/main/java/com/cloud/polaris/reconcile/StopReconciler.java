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
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StopReconciler {

    private final InstanceRepository instanceRepository;
    private final ComputeProvider computeProvider;
    private final InstanceLifecycleService instanceLifecycleService;

    @Scheduled(fixedDelay = 30_000)
    public void reconcile() {
        List<UUID> instanceIds = instanceRepository.findInstanceIdsForStopReconciliation(CurrentState.STOPPING, DesiredState.STOPPED, PageRequest.of(0, 50));
        for (UUID instanceId : instanceIds) {
            try {
                reconcileOne(instanceId);
            } catch (Exception exception) {
                log.error(
                        "Failed to reconcile stopping instance {}",
                        instanceId,
                        exception
                );
            }
        }
    }

    private void reconcileOne(UUID instanceId) {
        Optional<ProviderResource> resource = computeProvider.findByInstanceId(instanceId);

        if (resource.isEmpty()) {
            instanceLifecycleService.completeStopFromReconciliation(instanceId, true);
            return;
        }
        switch (resource.get().status()) {
            case RUNNING -> reconcileRunningResource(instanceId, resource.get());

            case STOPPED, CREATED -> instanceLifecycleService.completeStopFromReconciliation(
                    instanceId,
                    false
            );

            case UNKNOWN -> log.warn(
                    "Provider resource has unknown status for instance {}",
                    instanceId
            );
        }
    }

    private void reconcileRunningResource(UUID instanceId, ProviderResource resource) {
        computeProvider.stop(resource.providerResourceId());
        Optional<ProviderResource> afterStop  = computeProvider.findByInstanceId(instanceId);

        if (afterStop.isEmpty()) {
            instanceLifecycleService.completeStopFromReconciliation(instanceId, true);
            return;
        }

        if (afterStop.get().status() == ProviderResourceStatus.STOPPED || afterStop.get().status() == ProviderResourceStatus.CREATED) {
            instanceLifecycleService.completeStopFromReconciliation(instanceId, false);
        }
    }
}
