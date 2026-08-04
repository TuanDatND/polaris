package com.cloud.polaris.reconcile.queue;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReconcileQueueService {

    private final ReconcileRequestRepository reconcileRequestRepository;

    @Transactional
    public void wake(UUID instanceId, long generation) {
        Instant now = Instant.now();
        reconcileRequestRepository.wake(instanceId, generation, now);
    }

    @Transactional
    public List<ClaimedReconcileRequest> claimReady(int limit, String workerId, Duration leaseDuration) {
        Instant now = Instant.now();
        List<ReconcileRequest> claimedRequests = reconcileRequestRepository.findReadyForUpdate(now, limit);

        for (ReconcileRequest request : claimedRequests) {
            request.claim(workerId, UUID.randomUUID(), now.plus(leaseDuration));
        }

        return claimedRequests.stream().map(request -> new ClaimedReconcileRequest(
                        request.getInstanceId(),
                        request.getRequestedGeneration(),
                        request.getClaimToken()
                ))
                .toList();
    }

    @Transactional
    public int recoverExpiredLeases(int limit){
        Instant now = Instant.now();

        List<ReconcileRequest> expired = reconcileRequestRepository.findExpiredLeasesForUpdate(now, limit);

        expired.forEach(request -> request.recoverExpiredLease(now));
        return expired.size();
    }

    @Transactional
    public void requeue(ClaimedReconcileRequest claimed, Instant nextAvailableAt, String error){
        ReconcileRequest request = lockOwned(claimed);
        request.requeue(nextAvailableAt, error);
    }

    @Transactional
    public void scheduleResync(ClaimedReconcileRequest claimed, Instant nextAvailableAt){
        ReconcileRequest request = lockOwned(claimed);
        request.scheduleResync(nextAvailableAt);
    }

    @Transactional
    public void block(ClaimedReconcileRequest claimed, String error){
        ReconcileRequest request = lockOwned(claimed);
        request.block(error);
    }

    private ReconcileRequest lockOwned(ClaimedReconcileRequest claimed){
        ReconcileRequest request = reconcileRequestRepository.findByInstanceIdForUpdate(claimed.instanceId()).orElseThrow(()-> new ResourceNotFoundException("Reconcile request not found: " + claimed.instanceId()));
        request.assertClaimToken(claimed.claimToken());
        return request;
    }
}
