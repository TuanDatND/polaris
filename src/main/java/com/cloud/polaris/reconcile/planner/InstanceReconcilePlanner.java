package com.cloud.polaris.reconcile.planner;

import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.DesiredState;
import com.cloud.polaris.reconcile.domain.InstanceSnapshot;
import com.cloud.polaris.reconcile.domain.ProviderObservedState;
import com.cloud.polaris.reconcile.domain.ReconcileDecision;

import java.util.Objects;

public final class InstanceReconcilePlanner {

    public ReconcileDecision plan(InstanceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        if (snapshot.currentState() == CurrentState.DELETED && snapshot.desiredState() != DesiredState.DELETED) {
            return ReconcileDecision.INVALID;
        }

        if (snapshot.provisioningActive()) {
            return ReconcileDecision.WAIT;
        }

        if (snapshot.observedState() == ProviderObservedState.UNKNOWN) {
            return ReconcileDecision.WAIT;
        }

        if (snapshot.currentState() == CurrentState.FAILED && snapshot.desiredState() != DesiredState.DELETED) {
            return ReconcileDecision.INVALID;
        }

        return switch (snapshot.desiredState()) {
            case RUNNING -> switch (snapshot.observedState()) {
                case RUNNING -> ReconcileDecision.NOOP;
                case CREATED, STOPPED -> ReconcileDecision.START;
                case MISSING -> ReconcileDecision.INVALID;
                case UNKNOWN -> throw new IllegalStateException("Handled above");
            };

            case STOPPED -> switch (snapshot.observedState()) {
                case CREATED, STOPPED, MISSING -> ReconcileDecision.NOOP;
                case RUNNING -> ReconcileDecision.STOP;
                case UNKNOWN -> throw new IllegalStateException("Handled above");
            };

            case DELETED -> switch (snapshot.observedState()) {
                case CREATED, STOPPED -> ReconcileDecision.DELETE;
                case RUNNING -> ReconcileDecision.STOP;
                case MISSING -> ReconcileDecision.NOOP;
                case UNKNOWN -> throw new IllegalStateException("Handled above");
            };
        };
    }
}
