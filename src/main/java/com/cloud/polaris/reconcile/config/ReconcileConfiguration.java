package com.cloud.polaris.reconcile.config;

import com.cloud.polaris.reconcile.planner.InstanceReconcilePlanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReconcileConfiguration {

    @Bean
    InstanceReconcilePlanner instanceReconcilePlanner() {
        return new InstanceReconcilePlanner();
    }
}
