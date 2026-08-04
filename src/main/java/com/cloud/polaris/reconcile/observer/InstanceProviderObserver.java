package com.cloud.polaris.reconcile.observer;

import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.ProviderResourceStatus;
import com.cloud.polaris.reconcile.domain.ProviderObservation;
import com.cloud.polaris.reconcile.domain.ProviderObservedState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InstanceProviderObserver {

    private final ComputeProvider computeProvider;

    public ProviderObservation observe(UUID instanceId) {
        return computeProvider.findByInstanceId(instanceId)
                .map(resource -> new ProviderObservation(
                        map(resource.status()),
                        resource.providerResourceId()
                ))
                .orElseGet(() -> new ProviderObservation(
                        ProviderObservedState.MISSING,
                        null
                ));
    }

    private ProviderObservedState map(ProviderResourceStatus status) {
        return switch (status){
            case CREATED ->  ProviderObservedState.CREATED;
            case RUNNING ->  ProviderObservedState.RUNNING;
            case STOPPED ->  ProviderObservedState.STOPPED;
            case UNKNOWN ->  ProviderObservedState.UNKNOWN;
        };
    }

}
