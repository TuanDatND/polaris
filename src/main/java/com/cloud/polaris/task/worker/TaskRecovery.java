package com.cloud.polaris.task.worker;

import com.cloud.polaris.task.config.TaskProperties;
import com.cloud.polaris.task.service.TaskClaimService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRecovery {
    private final TaskClaimService taskClaimService;
    private final TaskProperties properties;

    @Scheduled(fixedDelayString = "${polaris.task.recovery-interval:30s}")
    public void recover() {
        int count = taskClaimService.recoverExpiredTasks(
                properties.recoveryTimeout(),
                properties.recoveryBatchSize()
        );
        log.debug("Recovered {} expired tasks", count);
    }
}
