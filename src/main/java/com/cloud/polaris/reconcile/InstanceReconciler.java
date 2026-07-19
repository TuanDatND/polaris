package com.cloud.polaris.reconcile;

import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.instance.service.InstanceCompensationService;
import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.ProviderResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InstanceReconciler {
    private final InstanceRepository instanceRepository;
    private final ComputeProvider computeProvider;
    private final InstanceCompensationService cleanupService;

    @Scheduled(fixedDelay = 30_000)
    public void reconcileFailedInstances() {
        List<UUID> instanceIds = instanceRepository.findFailedInstanceIdsForCleanup(CurrentState.FAILED);

        for (UUID instanceId : instanceIds) {
            try{
                reconcileOne(instanceId);
            }catch(Exception exception){
                log.error(
                        "Failed to reconcile instance {}",
                        instanceId,
                        exception
                );
            }
        }
    }

    private void reconcileOne(UUID instanceId) {
        Optional<ProviderResource> resource = computeProvider.findByInstanceId(instanceId);

        if (resource.isPresent()) {
            computeProvider.delete(
                    resource.get().providerResourceId()
            );

            resource = computeProvider.findByInstanceId(instanceId);
            if (resource.isPresent()) {
                return;
            }
        }
        cleanupService.releaseQuotaIfCleanupCompleted(instanceId);
    }
}
