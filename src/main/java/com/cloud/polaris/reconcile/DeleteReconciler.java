package com.cloud.polaris.reconcile;

import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.instance.service.InstanceCompensationService;
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
public class DeleteReconciler {

    private final InstanceRepository instanceRepository;
    private final ComputeProvider computeProvider;
    private final InstanceLifecycleService instanceLifecycleService;
    private final InstanceCompensationService cleanupService;

    @Scheduled(fixedDelay = 30_000)
    public void reconcile() {
        List<UUID> instanceIds = instanceRepository.findInstanceIdsForDeleteReconciliation(CurrentState.DELETING, DesiredState.DELETED, PageRequest.of(0, 50));

        for (UUID instanceId : instanceIds) {
            try {
                reconcileOne(instanceId);
            } catch (Exception exception) {
                log.error(
                        "Failed to reconcile deleting instance {}",
                        instanceId,
                        exception
                );
            }
        }
    }

    private void reconcileOne(UUID instanceId) {
        Optional<ProviderResource> resource = computeProvider.findByInstanceId(instanceId);

        if (resource.isEmpty()) {
            completeDeleteAndReleaseQuota(instanceId);
            return;
        }

        if (resource.get().status() == ProviderResourceStatus.UNKNOWN) {
            log.warn(
                    "Provider resource status unknown for deleting instance {}",
                    instanceId
            );
            return;
        }

        computeProvider.delete(
                resource.get().providerResourceId()
        );

        Optional<ProviderResource> afterDelete =
                computeProvider.findByInstanceId(instanceId);

        if (afterDelete.isEmpty()) {
            completeDeleteAndReleaseQuota(instanceId);
        }
    }

    private void completeDeleteAndReleaseQuota(UUID instanceId) {
        instanceLifecycleService.completeDeleteFromReconciliation(instanceId, true);

        cleanupService.releaseQuotaIfCleanupCompleted(instanceId);
    }
}
