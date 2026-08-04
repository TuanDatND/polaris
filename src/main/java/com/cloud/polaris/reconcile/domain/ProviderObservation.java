package com.cloud.polaris.reconcile.domain;

import java.util.Objects;

public record ProviderObservation(ProviderObservedState state, String providerResourceId) {
    public ProviderObservation {
        Objects.requireNonNull(state, "state must not be null");
    }
}
