package com.cloud.polaris.reconcile.domain;

public enum ReconcileDecision {
    NOOP,
    CREATE,
    START,
    STOP,
    DELETE,
    WAIT,
    INVALID
}
