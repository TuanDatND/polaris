package com.cloud.polaris.task.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "polaris.task")
public record TaskProperties(
        Duration pollInterval,
        int workerConcurrency,
        Duration recoveryTimeout,
        int recoveryBatchSize
) {
}
