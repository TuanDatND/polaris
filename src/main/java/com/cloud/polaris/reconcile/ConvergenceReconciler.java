package com.cloud.polaris.reconcile;

import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.task.service.repair.RepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConvergenceReconciler {

    private final InstanceRepository instanceRepository;
    private final RepairService repairService;

    @Scheduled(fixedDelay = 30_000)
    public void reconcile() {
        List<UUID> instanceIds = instanceRepository.findNonConvergedInstanceIds(PageRequest.of(0, 50));
        for (UUID instanceId : instanceIds) {
            try {
                repairService.reconcileOne(instanceId);
            } catch (Exception exception) {
                log.error("Exception occurred while reconcile instanceId {}", instanceId, exception);
            }
        }
    }

}
