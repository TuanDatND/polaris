package com.cloud.polaris.task.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class TaskExecutorConfig {

    @Bean(name = "taskWorkerExecutor")
    public ExecutorService taskWorkerExecutor(TaskProperties taskProperties) {
        ThreadFactory threadFactory = Thread.ofPlatform().name("polaris-task-worker-", 0).factory();

        return Executors.newFixedThreadPool(
                taskProperties.workerConcurrency(),
                threadFactory
        );
    }
}
