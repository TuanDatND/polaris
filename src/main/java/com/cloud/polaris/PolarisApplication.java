package com.cloud.polaris;

import com.cloud.polaris.task.config.TaskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(TaskProperties.class)
public class PolarisApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolarisApplication.class, args);
    }

}
