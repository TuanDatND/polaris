package com.cloud.polaris.instance.domain;

import com.cloud.polaris.reconcile.domain.ProviderObservedState;
import com.cloud.polaris.tenant.domain.Tenant;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceReconcileMetadataTest {

    @Test
    void should_IncrementGenerationOnly_whenDesiredStateActuallyChanges() {
        Instance instance = newInstance();

        assertThat(instance.getGeneration()).isEqualTo(1L);
        assertThat(instance.getObservedGeneration()).isZero();
        assertThat(instance.getLastObservedState())
                .isEqualTo(ProviderObservedState.UNKNOWN);

        assertThat(instance.requestStop()).isTrue();
        assertThat(instance.getGeneration()).isEqualTo(2L);

        assertThat(instance.requestStop()).isFalse();
        assertThat(instance.getGeneration()).isEqualTo(2L);

        assertThat(instance.requestDelete()).isTrue();
        assertThat(instance.getGeneration()).isEqualTo(3L);

        assertThatThrownBy(instance::requestStart)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot change desired state after deletion");
    }

    @Test
    void should_RecordProviderObservation_andNeverDecreaseObservedGeneration() {
        Instance instance = newInstance();
        Instant observedAt = Instant.parse("2026-08-04T00:00:00Z");

        instance.recordObservation(
                ProviderObservedState.STOPPED,
                observedAt
        );

        instance.markObservedGeneration(1L);
        instance.markObservedGeneration(0L);

        assertThat(instance.getLastObservedState())
                .isEqualTo(ProviderObservedState.STOPPED);
        assertThat(instance.getLastObservedAt()).isEqualTo(observedAt);
        assertThat(instance.getObservedGeneration()).isEqualTo(1L);

        assertThatThrownBy(() -> instance.markObservedGeneration(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Observed generation cannot exceed desired generation");
    }

    private Instance newInstance() {
        Tenant tenant = Tenant.create(
                "reconcile-metadata-tenant",
                2,
                2_048,
                2
        );

        return Instance.createPending(
                tenant,
                "metadata-instance",
                "nginx:latest",
                1,
                512
        );
    }
}