package com.cloud.polaris.instance.domain;


import com.cloud.polaris.common.exception.IllegalStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class InstanceStateMachine {

    private static final Map<CurrentState, Set<CurrentState>> ALLOWED_TRANSITIONS = Map.of(
            CurrentState.PENDING, Set.of(CurrentState.PROVISIONING, CurrentState.FAILED),
            CurrentState.PROVISIONING, Set.of(CurrentState.RUNNING, CurrentState.FAILED),
            CurrentState.STARTING, Set.of(CurrentState.RUNNING, CurrentState.FAILED),
            CurrentState.RUNNING, Set.of(CurrentState.STOPPING, CurrentState.DELETING, CurrentState.FAILED),
            CurrentState.STOPPING, Set.of(CurrentState.STOPPED, CurrentState.FAILED),
            CurrentState.STOPPED, Set.of(CurrentState.STARTING, CurrentState.DELETING),
            CurrentState.DELETING, Set.of(CurrentState.DELETED, CurrentState.FAILED),
            CurrentState.FAILED, Set.of(CurrentState.DELETING),
            CurrentState.DELETED, Set.of()
    );

    public boolean canTransition(CurrentState from, CurrentState to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public void transition(Instance instance, CurrentState to) {
        CurrentState from = instance.getCurrentState();

        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException(from, to, instance.getId());
        }

        instance.changeCurrentState(to);
    }

    public void transitionIfNecessary(
            Instance instance,
            CurrentState target
    ) {
        CurrentState current = instance.getCurrentState();

        if (current == target) {
            return; // Idempotent: gọi lại cũng không lỗi
        }

        transition(instance, target);
    }
}
