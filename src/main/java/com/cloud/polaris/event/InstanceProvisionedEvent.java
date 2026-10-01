package com.cloud.polaris.event;

import java.util.UUID;

public record InstanceProvisionedEvent(
        UUID instanceId,
        long generation
) {

}
