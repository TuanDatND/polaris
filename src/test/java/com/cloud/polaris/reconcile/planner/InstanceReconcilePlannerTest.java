package com.cloud.polaris.reconcile.planner;

import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.reconcile.domain.InstanceSnapshot;
import com.cloud.polaris.reconcile.domain.ProviderObservedState;
import com.cloud.polaris.reconcile.domain.ReconcileDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceReconcilePlannerTest {

    private final InstanceReconcilePlanner planner = new InstanceReconcilePlanner();

    @ParameterizedTest
    @MethodSource("stableCases")
    void should_ReturnExpectedDecision(
            DesiredState desired,
            CurrentState current,
            ProviderObservedState observed,
            ReconcileDecision expected
    ) {
        ReconcileDecision actual = planner.plan(
                new InstanceSnapshot(desired, current, observed, false)
        );

        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> stableCases() {
        return Stream.of(
                Arguments.of(
                        DesiredState.RUNNING,
                        CurrentState.STOPPED,
                        ProviderObservedState.STOPPED,
                        ReconcileDecision.START
                ),
                Arguments.of(
                        DesiredState.RUNNING,
                        CurrentState.RUNNING,
                        ProviderObservedState.RUNNING,
                        ReconcileDecision.NOOP
                ),
                Arguments.of(
                        DesiredState.RUNNING,
                        CurrentState.STOPPED,
                        ProviderObservedState.MISSING,
                        ReconcileDecision.INVALID
                ),
                Arguments.of(
                        DesiredState.STOPPED,
                        CurrentState.RUNNING,
                        ProviderObservedState.RUNNING,
                        ReconcileDecision.STOP
                ),
                Arguments.of(
                        DesiredState.STOPPED,
                        CurrentState.STOPPED,
                        ProviderObservedState.STOPPED,
                        ReconcileDecision.NOOP
                ),
                Arguments.of(
                        DesiredState.STOPPED,
                        CurrentState.STOPPED,
                        ProviderObservedState.MISSING,
                        ReconcileDecision.NOOP
                ),
                Arguments.of(
                        DesiredState.DELETED,
                        CurrentState.RUNNING,
                        ProviderObservedState.RUNNING,
                        ReconcileDecision.STOP
                ),
                Arguments.of(
                        DesiredState.DELETED,
                        CurrentState.STOPPED,
                        ProviderObservedState.STOPPED,
                        ReconcileDecision.DELETE
                ),
                Arguments.of(
                        DesiredState.DELETED,
                        CurrentState.DELETING,
                        ProviderObservedState.MISSING,
                        ReconcileDecision.NOOP
                )
        );
    }

    @Test
    void should_Wait_whenProvisioningIsActive() {
        ReconcileDecision decision = planner.plan(
                new InstanceSnapshot(
                        DesiredState.STOPPED,
                        CurrentState.PROVISIONING,
                        ProviderObservedState.RUNNING,
                        true
                )
        );

        assertThat(decision).isEqualTo(ReconcileDecision.WAIT);
    }

    @Test
    void should_Wait_whenProviderObservationIsUnknown() {
        ReconcileDecision decision = planner.plan(
                new InstanceSnapshot(
                        DesiredState.DELETED,
                        CurrentState.DELETING,
                        ProviderObservedState.UNKNOWN,
                        false
                )
        );

        assertThat(decision).isEqualTo(ReconcileDecision.WAIT);
    }

    @Test
    void should_RejectResurrectionOfDeletedInstance() {
        ReconcileDecision decision = planner.plan(
                new InstanceSnapshot(
                        DesiredState.RUNNING,
                        CurrentState.DELETED,
                        ProviderObservedState.MISSING,
                        false
                )
        );

        assertThat(decision).isEqualTo(ReconcileDecision.INVALID);
    }
}