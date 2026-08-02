package com.cloud.polaris.reconcile.domain;

public enum ReconcileDecision {
    NOOP,
    START,
    STOP,
    DELETE,
    WAIT,
    INVALID
}
