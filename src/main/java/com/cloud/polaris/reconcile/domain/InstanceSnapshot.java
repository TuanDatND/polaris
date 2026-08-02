package com.cloud.polaris.reconcile.domain;

import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;

public record InstanceSnapshot(
        DesiredState desiredState,
        CurrentState currentState,
        ProviderObservedState observedState,
        boolean provisioningActive
) {
}
