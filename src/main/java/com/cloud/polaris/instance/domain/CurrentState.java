package com.cloud.polaris.instance.domain;

public enum CurrentState {
    PENDING,
    PROVISIONING,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    DELETING,
    DELETED,
    FAILED
}
