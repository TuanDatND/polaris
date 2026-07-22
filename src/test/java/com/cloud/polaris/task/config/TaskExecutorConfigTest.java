package com.cloud.polaris.task.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExecutorConfigTest {

    @Test
    void should_CreateFixedThreadPoolWithExpectedConfiguration() throws Exception {
        TaskProperties properties = new TaskProperties(
                Duration.ofSeconds(1),
                2,
                Duration.ofMinutes(5),
                100
        );

        ExecutorService executor =
                new TaskExecutorConfig().taskWorkerExecutor(properties);

        try {
            assertThat(executor).isInstanceOf(ThreadPoolExecutor.class);
            ThreadPoolExecutor threadPool = (ThreadPoolExecutor) executor;
            assertThat(threadPool.getCorePoolSize()).isEqualTo(2);
            assertThat(threadPool.getMaximumPoolSize()).isEqualTo(2);

            CountDownLatch completed = new CountDownLatch(2);
            Set<String> threadNames = ConcurrentHashMap.newKeySet();

            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    threadNames.add(Thread.currentThread().getName());
                    completed.countDown();
                });
            }

            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(threadNames)
                    .allMatch(name -> name.startsWith("polaris-task-worker-"));
        } finally {
            executor.shutdownNow();
        }
    }
}
