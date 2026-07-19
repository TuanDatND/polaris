package com.cloud.polaris.task.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record ClaimedTask(
        UUID taskId,
        TaskType type,
        UUID instanceId,
        UUID tenantId,
        JsonNode payload,
        int attempts,
        int maxAttempts
) {
    public static ClaimedTask from(Task task) {
        return new ClaimedTask(
                task.getId(),
                task.getType(),
                task.getInstance().getId(),
                task.getTenant().getId(),
                task.getPayload(),
                task.getAttempts(),
                task.getMaxAttempts()
        );
    }
}
