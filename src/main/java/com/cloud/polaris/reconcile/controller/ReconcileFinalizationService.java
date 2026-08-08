package com.cloud.polaris.reconcile.controller;

import com.cloud.polaris.instance.domain.InstanceStateMachine;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.reconcile.queue.ClaimedReconcileRequest;
import com.cloud.polaris.reconcile.queue.ReconcileRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ReconcileFinalizationService {


    private static final Duration ACTION_RECHECK_DELAY = Duration.ofSeconds(1);
    private static final Duration WAIT_RECHECK_DELAY = Duration.ofSeconds(5);
    private static final Duration RESYNC_DELAY = Duration.ofSeconds(30);

    private final InstanceRepository instanceRepository;
    private final ReconcileRequestRepository reconcileRequestRepository;
    private final InstanceStateMachine stateMachine;

    @Transactional
    public void complete(ClaimedReconcileRequest claimed, PreparedReconcile prepared){

    }

}
