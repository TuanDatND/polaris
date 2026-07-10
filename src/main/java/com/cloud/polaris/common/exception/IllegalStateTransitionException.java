package com.cloud.polaris.common.exception;

import com.cloud.polaris.instance.domain.CurrentState;

import java.util.UUID;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(CurrentState from, CurrentState to, UUID instanceId) {
        super("Illegal state transition for instance " + instanceId + ": " + from + " -> " + to);
    }
}
